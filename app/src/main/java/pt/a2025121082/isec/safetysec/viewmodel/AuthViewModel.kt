package pt.a2025121082.isec.safetysec.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseUser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import pt.a2025121082.isec.safetysec.data.model.AuthState
import pt.a2025121082.isec.safetysec.data.repository.AuthRepository
import javax.inject.Inject

/**
 * ViewModel responsible for authentication-related UI logic.
 *
 * It exposes a single [AuthState] to the UI and delegates all Firebase operations
 * to [AuthRepository].
 *
 * Supported flows:
 * - Registration (creates account + sends verification email + stores profile in Firestore)
 * - Login (sign-in + email verification check)
 * - Password reset
 * - Resend verification email (MFA helper)
 * - Refresh auth state (e.g., after returning from email verification)
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val repository: AuthRepository
) : ViewModel() {

    /** UI state observed by Compose screens. */
    var uiState by mutableStateOf(AuthState())
        private set

    /**
     * Registration flow:
     * - creates an account in Firebase Auth
     * - sends an email verification message (basic MFA)
     * - stores the user profile in Firestore
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
     * - signs in via Firebase Auth
     * - reloads the FirebaseUser and checks emailVerified (MFA)
     * - if not verified, forces logout and blocks access
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

                // Important: reload() updates isEmailVerified after the user clicks the link in the email.
                firebaseUser.reload().await()

                if (firebaseUser.isEmailVerified) {
                    uiState = uiState.copy(isAuthenticated = true, message = null)
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
     * Logs out the user and resets the UI state.
     */
    fun logout() {
        repository.logout()
        uiState = AuthState()
    }

    /**
     * Sends password reset instructions to the provided email.
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
     * Resends the verification email (MFA helper).
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
     * Refreshes the authentication state (e.g., after returning to the app from email verification).
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
            } catch (ex: Exception) {
                uiState = uiState.copy(error = "Failed to refresh auth state: ${ex.message}")
            } finally {
                uiState = uiState.copy(isLoading = false)
            }
        }
    }

    /** @return The current Firebase user (if authenticated), otherwise null. */
    fun currentFirebaseUser(): FirebaseUser? = repository.getCurrentUser()

    /** Clears the current error message from the UI state. */
    fun clearError() {
        uiState = uiState.copy(error = null)
    }

    /** Clears the current info message from the UI state. */
    fun clearMessage() {
        uiState = uiState.copy(message = null)
    }

    /** Consumes (resets) the registration success flag after navigation. */
    fun consumeRegistrationSuccess() {
        uiState = uiState.copy(isRegistrationSuccessful = false)
    }
}