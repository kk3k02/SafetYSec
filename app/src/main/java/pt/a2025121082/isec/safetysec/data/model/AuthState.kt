package pt.a2025121082.isec.safetysec.data.model

/**
 * UI state for authentication flows (login / registration / password reset / account updates).
 *
 * This state is used by the AuthViewModel to expose the current status 
 * of authentication operations to the UI components.
 */
data class AuthState(
    /** Indicates if a network request or background auth operation is currently running. */
    val isLoading: Boolean = false,

    /** Indicates if the registration process completed successfully. */
    val isRegistrationSuccessful: Boolean = false,

    /** Indicates if the user is currently signed in and has a valid session. */
    val isAuthenticated: Boolean = false,

    /** Stores an error message if an operation (like login) fails. */
    val error: String? = null,

    /** Stores a success or informational message for the user. */
    val message: String? = null,

    /** The display name of the currently authenticated user. */
    val accountName: String? = null,

    /** The email address of the currently authenticated user. */
    val accountEmail: String? = null
)
