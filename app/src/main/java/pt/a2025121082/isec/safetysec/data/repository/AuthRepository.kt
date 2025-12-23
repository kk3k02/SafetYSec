package pt.a2025121082.isec.safetysec.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import pt.a2025121082.isec.safetysec.data.model.User
import javax.inject.Inject

/**
 * Repository responsible for:
 * - authentication (FirebaseAuth)
 * - user profile management (Firestore)
 * - OTP-based linking between Monitor <-> Protected users
 */
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    /** Convenience accessor for the "users" collection in Firestore. */
    private val usersCol get() = firestore.collection("users")

    /** OTP validity duration (e.g., 10 minutes). */
    private val ASSOCIATION_CODE_TTL_MS = 10 * 60 * 1000L

    // -------------------------
    // AUTH (FirebaseAuth)
    // -------------------------

    /**
     * Registers a new user using email + password and creates the user profile in Firestore.
     * Also sends an email verification message (basic MFA approach).
     */
    suspend fun registerUser(email: String, password: String, name: String) {
        val cleanEmail = email.trim()
        val cleanName = name.trim()

        require(cleanEmail.isNotBlank()) { "Email cannot be empty." }
        require(password.isNotBlank()) { "Password cannot be empty." }
        require(cleanName.isNotBlank()) { "Name cannot be empty." }

        val result = auth.createUserWithEmailAndPassword(cleanEmail, password).await()
        val firebaseUser = result.user ?: throw IllegalStateException("User not found after registration.")

        // MFA: email verification
        firebaseUser.sendEmailVerification().await()

        // Create Firestore user profile
        val newUser = User(
            uid = firebaseUser.uid,
            email = cleanEmail,
            name = cleanName,
            roles = listOf("Protected"),     // default role
            alertCancelCode = "0000",        // default PIN
            monitors = emptyList(),
            protectedUsers = emptyList(),
            associationCode = null
            // If you add this field to User:
            // associationCodeCreatedAt = null
        )

        usersCol.document(newUser.uid).set(newUser).await()
    }

    /**
     * Logs in a user using email + password.
     */
    suspend fun loginUser(email: String, password: String) {
        val cleanEmail = email.trim()
        require(cleanEmail.isNotBlank()) { "Email cannot be empty." }
        require(password.isNotBlank()) { "Password cannot be empty." }

        auth.signInWithEmailAndPassword(cleanEmail, password).await()
    }

    /**
     * Sends a password reset email to the provided address.
     */
    suspend fun sendPasswordResetEmail(email: String) {
        val cleanEmail = email.trim()
        require(cleanEmail.isNotBlank()) { "Email cannot be empty." }

        auth.sendPasswordResetEmail(cleanEmail).await()
    }

    /** @return The currently authenticated Firebase user, or null if none. */
    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    /** Signs out the current user. */
    fun logout() {
        auth.signOut()
    }

    // -------------------------
    // PROFILE (Firestore: users/{uid})
    // -------------------------

    /**
     * Loads the user profile from Firestore.
     *
     * @param uid User id to load; defaults to the currently authenticated uid.
     */
    suspend fun getUserProfile(uid: String = requireCurrentUid()): User {
        val snap = usersCol.document(uid).get().await()
        return snap.toObject(User::class.java)
            ?: throw IllegalStateException("User profile not found in Firestore.")
    }

    /**
     * Updates the user's display name in Firestore.
     */
    suspend fun updateUserName(newName: String, uid: String = requireCurrentUid()) {
        val cleanName = newName.trim()
        require(cleanName.isNotBlank()) { "Name cannot be empty." }

        usersCol.document(uid).update("name", cleanName).await()
    }

    /**
     * Updates the alert cancellation code (PIN) used during the 10-second cancel window.
     */
    suspend fun updateAlertCancelCode(newCode: String, uid: String = requireCurrentUid()) {
        val code = newCode.trim()
        require(code.length in 4..8) { "Cancel code should be 4-8 digits/chars." }

        usersCol.document(uid).update("alertCancelCode", code).await()
    }

    /**
     * Adds a role to the user's roles array field.
     */
    suspend fun addRole(role: String, uid: String = requireCurrentUid()) {
        val r = role.trim()
        require(r.isNotBlank()) { "Role cannot be empty." }

        usersCol.document(uid).update("roles", FieldValue.arrayUnion(r)).await()
    }

    /**
     * Removes a role from the user's roles array field.
     */
    suspend fun removeRole(role: String, uid: String = requireCurrentUid()) {
        val r = role.trim()
        require(r.isNotBlank()) { "Role cannot be empty." }

        usersCol.document(uid).update("roles", FieldValue.arrayRemove(r)).await()
    }

    // -------------------------
    // OTP (Association)
    // -------------------------

    /**
     * Protected user generates a 6-digit OTP and shares it with a Monitor.
     * The code is one-time use. A creation timestamp is stored for TTL checks.
     *
     * @return Generated association code.
     */
    suspend fun generateAssociationCode(): String {
        val uid = requireCurrentUid()

        // Try a few times to avoid collisions (rare but possible).
        repeat(5) {
            val code = (100000..999999).random().toString()
            val existing = usersCol.whereEqualTo("associationCode", code).get().await()
            if (existing.isEmpty) {
                usersCol.document(uid).update(
                    mapOf(
                        "associationCode" to code,
                        "associationCodeCreatedAt" to System.currentTimeMillis()
                    )
                ).await()
                return code
            }
        }

        // If we hit collisions too many times (extremely unlikely).
        throw IllegalStateException("Failed to generate unique association code.")
    }

    /**
     * Monitor enters the OTP received from Protected.
     * Checks:
     * - monitor cannot link to themselves
     * - code exists and is not expired
     * - updates are done transactionally to prevent race conditions
     */
    suspend fun linkWithAssociationCode(inputCode: String) {
        val monitorId = requireCurrentUid()
        val code = inputCode.trim()

        require(code.isNotBlank()) { "Association code cannot be empty." }

        // Find Protected user with the given code.
        val querySnap = usersCol.whereEqualTo("associationCode", code).get().await()
        if (querySnap.isEmpty) throw IllegalArgumentException("Invalid association code.")

        val protectedDoc = querySnap.documents.first()
        val protectedId = protectedDoc.id

        if (protectedId == monitorId) {
            throw IllegalArgumentException("Cannot monitor yourself.")
        }

        firestore.runTransaction { tx ->
            val protectedRef = usersCol.document(protectedId)
            val monitorRef = usersCol.document(monitorId)

            val protectedSnap = tx.get(protectedRef)
            val monitorSnap = tx.get(monitorRef)

            if (!protectedSnap.exists()) throw IllegalStateException("Protected user not found.")
            if (!monitorSnap.exists()) throw IllegalStateException("Monitor user not found.")

            // Confirm the code is still present and unchanged (race-condition protection).
            val currentCode = protectedSnap.getString("associationCode")
            if (currentCode != code) {
                throw IllegalArgumentException("Association code is no longer valid.")
            }

            // TTL check (if the field exists).
            val createdAt = protectedSnap.getLong("associationCodeCreatedAt")
            if (createdAt != null) {
                val age = System.currentTimeMillis() - createdAt
                if (age > ASSOCIATION_CODE_TTL_MS) {
                    // Remove expired code.
                    tx.update(protectedRef, "associationCode", FieldValue.delete())
                    tx.update(protectedRef, "associationCodeCreatedAt", FieldValue.delete())
                    throw IllegalArgumentException("Association code expired. Generate a new one.")
                }
            }

            // Two-way association.
            tx.update(protectedRef, "monitors", FieldValue.arrayUnion(monitorId))
            tx.update(monitorRef, "protectedUsers", FieldValue.arrayUnion(protectedId))

            // Ensure Monitor role exists.
            tx.update(monitorRef, "roles", FieldValue.arrayUnion("Monitor"))

            // Remove the consumed OTP.
            tx.update(protectedRef, "associationCode", FieldValue.delete())
            tx.update(protectedRef, "associationCodeCreatedAt", FieldValue.delete())
        }.await()
    }

    /**
     * Removes an existing association between a Monitor and a Protected user.
     */
    suspend fun removeAssociation(monitorId: String, protectedId: String) {
        require(monitorId.isNotBlank() && protectedId.isNotBlank())

        firestore.runTransaction { tx ->
            val monitorRef = usersCol.document(monitorId)
            val protectedRef = usersCol.document(protectedId)

            tx.update(monitorRef, "protectedUsers", FieldValue.arrayRemove(protectedId))
            tx.update(protectedRef, "monitors", FieldValue.arrayRemove(monitorId))
        }.await()
    }

    /**
     * Protected user can manually clear the OTP (e.g., "Cancel code" action).
     */
    suspend fun clearAssociationCode(uid: String = requireCurrentUid()) {
        usersCol.document(uid).update(
            mapOf(
                "associationCode" to FieldValue.delete(),
                "associationCodeCreatedAt" to FieldValue.delete()
            )
        ).await()
    }

    // -------------------------
    // Helpers / Lists
    // -------------------------

    /** @return List of monitor IDs linked to the current (or provided) user. */
    suspend fun getMyMonitors(uid: String = requireCurrentUid()): List<String> =
        getUserProfile(uid).monitors

    /** @return List of protected user IDs linked to the current (or provided) user. */
    suspend fun getMyProtectedUsers(uid: String = requireCurrentUid()): List<String> =
        getUserProfile(uid).protectedUsers

    // -------------------------
    // Private helpers
    // -------------------------

    /**
     * @return Current authenticated user's uid, or throws if not logged in.
     */
    private fun requireCurrentUid(): String =
        auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated.")
}