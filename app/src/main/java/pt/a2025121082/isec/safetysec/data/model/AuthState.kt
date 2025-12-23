package pt.a2025121082.isec.safetysec.data.model

/**
 * UI state for authentication flows (login / registration / password reset / MFA).
 *
 * Used by the ViewModel to expose the current authentication status to the UI.
 */
data class AuthState(

    /** True when an authentication-related operation is in progress */
    val isLoading: Boolean = false,

    /** True when the registration process finished successfully */
    val isRegistrationSuccessful: Boolean = false,

    /** True when the user is currently authenticated (logged in) */
    val isAuthenticated: Boolean = false,

    /** Optional error message to display in the UI */
    val error: String? = null,

    /** Optional informational message to display in the UI (e.g., success confirmation) */
    val message: String? = null
)