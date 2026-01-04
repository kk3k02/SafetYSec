package pt.a2025121082.isec.safetysec.data.repository

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import pt.a2025121082.isec.safetysec.data.model.User
import javax.inject.Inject

/**
 * Repository responsible for user authentication and profile management.
 * Integrates Firebase Auth for credentials and Firestore for extended user data.
 */
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    private val usersCol get() = firestore.collection("users")
    private val ASSOCIATION_CODE_TTL_MS = 10 * 60 * 1000L

    // --- AUTHENTICATION METHODS ---

    /**
     * Registers a new user with Firebase Auth and creates a profile in Firestore.
     * New users are assigned the "Protected" role by default.
     */
    suspend fun registerUser(email: String, password: String, name: String) {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val firebaseUser = result.user ?: throw IllegalStateException("User creation failed.")
        
        // Mandatory email verification for security
        firebaseUser.sendEmailVerification().await()

        val newUser = User(
            uid = firebaseUser.uid,
            email = email.trim(),
            name = name.trim(),
            roles = listOf("Protected"),
            alertCancelCode = "0000",
            inactivityDurationMin = 15
        )
        usersCol.document(newUser.uid).set(newUser).await()
    }

    /** Signs in an existing user with email and password. */
    suspend fun loginUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    /** Triggers a password reset email from Firebase Auth. */
    suspend fun sendPasswordResetEmail(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    /** Signs out the current user. */
    fun logout() = auth.signOut()

    /** Returns the underlying FirebaseUser object for the current session. */
    fun getCurrentUser(): FirebaseUser? = auth.currentUser

    /** Returns the unique identifier (UID) of the current user. */
    fun getCurrentUid(): String? = auth.currentUser?.uid

    /** Updates the user's login password. */
    suspend fun updatePassword(newPassword: String) {
        auth.currentUser?.updatePassword(newPassword)?.await() ?: throw IllegalStateException("Not authenticated.")
    }

    /** Starts the process to update the user's email address. */
    suspend fun updateEmail(newEmail: String) {
        val user = auth.currentUser ?: throw IllegalStateException("Not authenticated.")
        user.verifyBeforeUpdateEmail(newEmail.trim()).await()
    }

    /** Re-authenticates the current user. Required for sensitive operations like email/password changes. */
    suspend fun reauthenticate(password: String) {
        val user = auth.currentUser ?: throw IllegalStateException("Not authenticated.")
        val email = user.email ?: throw IllegalStateException("User email not found.")
        val credential = EmailAuthProvider.getCredential(email, password)
        user.reauthenticate(credential).await()
    }

    // --- PROFILE MANAGEMENT METHODS ---

    /** 
     * Retrieves the extended user profile from Firestore.
     * Defaults to current user if no UID is provided.
     */
    suspend fun getUserProfile(uid: String = requireCurrentUid()): User {
        return try {
            val snap = usersCol.document(uid).get().await()
            snap.toObject(User::class.java) ?: User(uid = uid)
        } catch (e: Exception) {
            User(uid = uid)
        }
    }

    /** Updates the user's display name in their profile. */
    suspend fun updateUserName(newName: String) {
        usersCol.document(requireCurrentUid()).update("name", newName.trim()).await()
    }

    /** Updates the 4-digit PIN used to cancel emergency alerts. */
    suspend fun updateAlertCancelCode(newCode: String) {
        usersCol.document(requireCurrentUid()).update("alertCancelCode", newCode.trim()).await()
    }

    /** Updates the threshold for prolonged inactivity detection in the profile. */
    suspend fun updateInactivityDuration(minutes: Int) {
        usersCol.document(requireCurrentUid()).update("inactivityDurationMin", minutes).await()
    }

    /** Records the timestamp when the monitor last cleared their incoming alerts. */
    suspend fun updateMonitorAlertsClearedAt(timestamp: Long) {
        usersCol.document(requireCurrentUid()).update("monitorAlertsClearedAt", timestamp).await()
    }

    // --- ASSOCIATION (OTP) METHODS ---

    /**
     * Generates a unique 6-digit code for linking a Protected user to a Monitor.
     * The code is stored in the Protected user's profile.
     */
    suspend fun generateAssociationCode(): String {
        val uid = requireCurrentUid()
        repeat(5) {
            val code = (100000..999999).random().toString()
            val existing = usersCol.whereEqualTo("associationCode", code).get().await()
            if (existing.isEmpty) {
                usersCol.document(uid).update(mapOf(
                    "associationCode" to code, 
                    "associationCodeCreatedAt" to System.currentTimeMillis()
                )).await()
                return code
            }
        }
        throw IllegalStateException("Failed to generate code.")
    }

    /**
     * Links the current user (Monitor) with another user (Protected) using their OTP code.
     * Updates roles and bidirectional relationship fields in Firestore.
     */
    suspend fun linkWithAssociationCode(inputCode: String) {
        val monitorId = requireCurrentUid()
        val querySnap = usersCol.whereEqualTo("associationCode", inputCode.trim()).get().await()
        if (querySnap.isEmpty) throw IllegalArgumentException("Invalid code.")

        val protectedId = querySnap.documents.first().id
        if (protectedId == monitorId) throw IllegalArgumentException("Cannot monitor yourself.")

        firestore.runTransaction { tx ->
            val pRef = usersCol.document(protectedId)
            val mRef = usersCol.document(monitorId)
            tx.update(pRef, "monitors", FieldValue.arrayUnion(monitorId))
            tx.update(mRef, "protectedUsers", FieldValue.arrayUnion(protectedId))
            tx.update(mRef, "roles", FieldValue.arrayUnion("Monitor"))
            tx.update(pRef, "associationCode", FieldValue.delete())
            tx.update(pRef, "associationCodeCreatedAt", FieldValue.delete())
        }.await()
    }

    /** Removes the monitoring link between two users. */
    suspend fun removeAssociation(monitorId: String, protectedId: String) {
        firestore.runTransaction { tx ->
            tx.update(usersCol.document(monitorId), "protectedUsers", FieldValue.arrayRemove(protectedId))
            tx.update(usersCol.document(protectedId), "monitors", FieldValue.arrayRemove(monitorId))
        }.await()
    }

    /** Helper function to ensure operations are only performed for authenticated users. */
    private fun requireCurrentUid(): String = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated.")
}
