package pt.a2025121082.isec.safetysec.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import pt.a2025121082.isec.safetysec.data.model.AuthState
import pt.a2025121082.isec.safetysec.data.repository.AuthRepository
import javax.inject.Inject

/**
 * ViewModel responsible for authentication-related UI logic.
 *
 * Supported flows:
 * - Registration: creates account, sends verification email, and stores profile in Firestore.
 * - Login: handles sign-in and enforces email verification check.
 * - Password reset: sends recovery instructions via email.
 * - Profile Management: handles loading and updating user info (name, email, password).
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository,
    private val db: FirebaseFirestore
) : ViewModel() {

    /** UI state observed by Compose screens to react to auth changes. */
    var uiState by mutableStateOf(AuthState())
        private set

    /**
     * Loads account info for the Profile UI.
     * Fetches the display name from Firestore and the email from Firebase Auth.
     */
    fun loadAccountInfo() {
        viewModelScope.launch {
            try {
                val user = repository.getCurrentUser()
                if (user == null) {
                    uiState = uiState.copy(accountName = null, accountEmail = null)
                    return@launch
                }

                val uid = user.uid
                val email = user.email

                // Fetch additional user data (like name) from Firestore
                val snap = db.collection("users").document(uid).get().await()
                val name = snap.getString("name")
                val storedEmail = snap.getString("email")
                
                // Sync email in Firestore if it differs from Auth (e.g., after an update)
                if (!email.isNullOrBlank() && email != storedEmail) {
                    db.collection("users").document(uid).update("email", email).await()
                }

                uiState = uiState.copy(
                    accountName = name,
                    accountEmail = email
                )
            } catch (ex: Exception) {
                uiState = uiState.copy(error = "Failed to load account info: ${ex.message}")
            }
        }
    }

    /**
     * Registration flow:
     * 1. Creates a new user in Firebase Auth.
     * 2. Sends an email verification (mandatory for this app).
     * 3. Stores the initial user profile in Firestore.
     */
    fun register(email: String, password: String, name: String) {
        val e = email.trim()
        val n = name.trim()

        if (e.isBlank() || password.isBlank() || n.isBlank()) {
            uiState = uiState.copy(error = "Please fill in all fields.")
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(
                isLoading = true,
                error = null,
                message = null,
                isRegistrationSuccessful = false
            )

            try {
                repository.registerUser(e, password, n)

                uiState = uiState.copy(
                    isRegistrationSuccessful = true,
                    isAuthenticated = false,
                    message = "Account created. Please verify your email before logging in."
                )
            } catch (ex: Exception) {
                uiState = uiState.copy(error = "Registration failed: ${ex.message}")
            } finally {
                uiState = uiState.copy(isLoading = false)
            }
        }
    }

    /**
     * Login flow:
     * 1. Signs in with credentials.
     * 2. Reloads user data to check if email has been verified.
     * 3. If verified, grants access; otherwise, logs out and prompts verification.
     */
    fun login(email: String, password: String) {
        val e = email.trim()

        if (e.isBlank() || password.isBlank()) {
            uiState = uiState.copy(error = "Email and password are required.")
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null, message = null)
            try {
                repository.loginUser(e, password)

                val firebaseUser = repository.getCurrentUser()
                if (firebaseUser == null) {
                    uiState = uiState.copy(error = "Authentication failed. Please try again.")
                    return@launch
                }

                // Force a reload to get the latest 'isEmailVerified' status
                firebaseUser.reload().await()

                if (firebaseUser.isEmailVerified) {
                    uiState = uiState.copy(isAuthenticated = true, message = null)
                    loadAccountInfo() // Sync profile data after successful login
                } else {
                    repository.logout()
                    uiState = uiState.copy(
                        isAuthenticated = false,
                        error = "Email verification required. Please verify your email and try again."
                    )
                }
            } catch (ex: Exception) {
                uiState = uiState.copy(error = "Login error: ${ex.message}")
            } finally {
                uiState = uiState.copy(isLoading = false)
            }
        }
    }

    /**
     * Logs out the user and clears the UI state.
     */
    fun logout() {
        repository.logout()
        uiState = AuthState()
    }

    /**
     * Triggers the Firebase password reset flow for the given email.
     */
    fun sendPasswordResetEmail(email: String) {
        val e = email.trim()
        if (e.isBlank()) {
            uiState = uiState.copy(error = "Email is required.")
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null, message = null)
            try {
                repository.sendPasswordResetEmail(e)
                uiState = uiState.copy(message = "Password reset instructions were sent to your email.")
            } catch (ex: Exception) {
                uiState = uiState.copy(error = "Password reset failed: ${ex.message}")
            } finally {
                uiState = uiState.copy(isLoading = false)
            }
        }
    }

    /**
     * Manually resends the verification email to the currently logged-in (but unverified) user.
     */
    fun resendVerificationEmail() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null, message = null)
            try {
                val user = repository.getCurrentUser()
                if (user == null) {
                    uiState = uiState.copy(error = "No authenticated user.")
                    return@launch
                }

                user.sendEmailVerification().await()
                uiState = uiState.copy(message = "Verification email sent. Please check your inbox.")
            } catch (ex: Exception) {
                uiState = uiState.copy(error = "Failed to send verification email: ${ex.message}")
            } finally {
                uiState = uiState.copy(isLoading = false)
            }
        }
    }

    /**
     * Refreshes the auth state. Useful when a user returns to the app after clicking the verification link.
     */
    fun refreshAuthState() {
        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null, message = null)
            try {
                val user = repository.getCurrentUser()
                if (user == null) {
                    uiState = uiState.copy(isAuthenticated = false, isLoading = false)
                    return@launch
                }

                user.reload().await()
                uiState = uiState.copy(isAuthenticated = user.isEmailVerified)

                if (user.isEmailVerified) {
                    loadAccountInfo()
                }
            } catch (ex: Exception) {
                uiState = uiState.copy(error = "Failed to refresh auth state: ${ex.message}")
            } finally {
                uiState = uiState.copy(isLoading = false)
            }
        }
    }

    /**
     * Updates the user's profile information.
     * Changing the email requires re-authentication with the current password.
     */
    fun updateProfile(newName: String, newEmail: String, currentPassword: String?) {
        val name = newName.trim()
        val email = newEmail.trim()

        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null, message = null)
            try {
                val user = repository.getCurrentUser()
                if (user == null) {
                    uiState = uiState.copy(error = "No authenticated user.")
                    return@launch
                }

                val uid = user.uid
                val oldEmail = user.email.orEmpty()

                // 1. Update name in Firestore
                if (name.isNotBlank()) {
                    db.collection("users").document(uid)
                        .update(mapOf("name" to name))
                        .await()
                }

                // 2. Handle email change request
                var emailChangeRequested = false
                if (email.isNotBlank() && email != oldEmail) {
                    val pwd = currentPassword?.trim().orEmpty()
                    if (pwd.isBlank()) {
                        uiState = uiState.copy(error = "Current password is required to change email.")
                        return@launch
                    }

                    // Re-authenticate to allow sensitive operation
                    val cred = EmailAuthProvider.getCredential(oldEmail, pwd)
                    user.reauthenticate(cred).await()

                    // Start email update process (requires verification of the new address)
                    user.verifyBeforeUpdateEmail(email).await()
                    emailChangeRequested = true
                }

                val message = if (emailChangeRequested) {
                    "Verification email sent to the new address. Confirm it to finish the update."
                } else {
                    "Profile updated successfully."
                }
                uiState = uiState.copy(message = message)
                loadAccountInfo()
            } catch (ex: Exception) {
                uiState = uiState.copy(error = "Profile update failed: ${ex.message}")
            } finally {
                uiState = uiState.copy(isLoading = false)
            }
        }
    }

    /**
     * Changes the user's password. Requires re-authentication for security.
     */
    fun changePassword(currentPassword: String, newPassword: String) {
        val currentPwd = currentPassword.trim()
        val newPwd = newPassword.trim()

        if (currentPwd.isBlank() || newPwd.isBlank()) {
            uiState = uiState.copy(error = "Current and new password are required.")
            return
        }
        if (newPwd.length < 6) {
            uiState = uiState.copy(error = "New password must be at least 6 characters.")
            return
        }

        viewModelScope.launch {
            uiState = uiState.copy(isLoading = true, error = null, message = null)
            try {
                val user = repository.getCurrentUser()
                val email = user?.email.orEmpty()
                if (user == null || email.isBlank()) {
                    uiState = uiState.copy(error = "No authenticated user.")
                    return@launch
                }

                // Sensitive operations require recent login or re-authentication
                val cred = EmailAuthProvider.getCredential(email, currentPwd)
                user.reauthenticate(cred).await()
                user.updatePassword(newPwd).await()

                uiState = uiState.copy(message = "Password changed successfully.")
            } catch (ex: Exception) {
                uiState = uiState.copy(error = "Password change failed: ${ex.message}")
            } finally {
                uiState = uiState.copy(isLoading = false)
            }
        }
    }

    /** Returns the underlying FirebaseUser object if available. */
    fun currentFirebaseUser(): FirebaseUser? = repository.getCurrentUser()

    /** Resets any error message currently displayed in the UI. */
    fun clearError() {
        uiState = uiState.copy(error = null)
    }

    /** Resets any status message currently displayed in the UI. */
    fun clearMessage() {
        uiState = uiState.copy(message = null)
    }

    /** Resets the registration success flag after it has been handled by the UI. */
    fun consumeRegistrationSuccess() {
        uiState = uiState.copy(isRegistrationSuccessful = false)
    }
}
