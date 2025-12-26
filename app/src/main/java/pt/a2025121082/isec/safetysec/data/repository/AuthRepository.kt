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
 * Repository responsible for authentication and user profile management.
 */
class AuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) {
    private val usersCol get() = firestore.collection("users")
    private val ASSOCIATION_CODE_TTL_MS = 10 * 60 * 1000L

    // --- AUTH METHODS ---

    suspend fun registerUser(email: String, password: String, name: String) {
        val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
        val firebaseUser = result.user ?: throw IllegalStateException("User not found.")
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

    suspend fun loginUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email.trim(), password).await()
    }

    suspend fun sendPasswordResetEmail(email: String) {
        auth.sendPasswordResetEmail(email.trim()).await()
    }

    fun logout() = auth.signOut()

    /** Returns the currently authenticated Firebase user. */
    fun getCurrentUser(): FirebaseUser? = auth.currentUser
    
    suspend fun updatePassword(newPassword: String) {
        auth.currentUser?.updatePassword(newPassword)?.await() ?: throw IllegalStateException("Not authenticated.")
    }

    suspend fun updateEmail(newEmail: String) {
        val user = auth.currentUser ?: throw IllegalStateException("Not authenticated.")
        user.updateEmail(newEmail.trim()).await()
        usersCol.document(user.uid).update("email", newEmail.trim()).await()
    }

    suspend fun reauthenticate(password: String) {
        val user = auth.currentUser ?: throw IllegalStateException("Not authenticated.")
        val email = user.email ?: throw IllegalStateException("User email not found.")
        val credential = EmailAuthProvider.getCredential(email, password)
        user.reauthenticate(credential).await()
    }

    // --- PROFILE METHODS ---

    suspend fun getUserProfile(uid: String = requireCurrentUid()): User {
        val snap = usersCol.document(uid).get().await()
        return snap.toObject(User::class.java) ?: throw IllegalStateException("Profile not found.")
    }

    suspend fun updateUserName(newName: String) {
        usersCol.document(requireCurrentUid()).update("name", newName.trim()).await()
    }

    suspend fun updateAlertCancelCode(newCode: String) {
        usersCol.document(requireCurrentUid()).update("alertCancelCode", newCode.trim()).await()
    }

    /**
     * Updates the global inactivity threshold in the user's profile.
     */
    suspend fun updateInactivityDuration(minutes: Int) {
        usersCol.document(requireCurrentUid()).update("inactivityDurationMin", minutes).await()
    }

    // --- ASSOCIATION (OTP) METHODS ---

    suspend fun generateAssociationCode(): String {
        val uid = requireCurrentUid()
        repeat(5) {
            val code = (100000..999999).random().toString()
            val existing = usersCol.whereEqualTo("associationCode", code).get().await()
            if (existing.isEmpty) {
                usersCol.document(uid).update(mapOf("associationCode" to code, "associationCodeCreatedAt" to System.currentTimeMillis())).await()
                return code
            }
        }
        throw IllegalStateException("Failed to generate code.")
    }

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

    suspend fun removeAssociation(monitorId: String, protectedId: String) {
        firestore.runTransaction { tx ->
            tx.update(usersCol.document(monitorId), "protectedUsers", FieldValue.arrayRemove(protectedId))
            tx.update(usersCol.document(protectedId), "monitors", FieldValue.arrayRemove(monitorId))
        }.await()
    }

    private fun requireCurrentUid(): String = auth.currentUser?.uid ?: throw IllegalStateException("Not authenticated.")
}
