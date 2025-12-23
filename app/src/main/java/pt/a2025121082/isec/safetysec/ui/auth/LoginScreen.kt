package pt.a2025121082.isec.safetysec.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import pt.a2025121082.isec.safetysec.viewmodel.AuthViewModel

/**
 * Login screen (Jetpack Compose).
 *
 * Responsibilities:
 * - Collect user credentials (email + password)
 * - Trigger login via AuthViewModel
 * - Show errors/messages using Snackbars
 * - Navigate on successful authentication
 * - Offer MFA helper: resend verification email if needed
 * - Offer password reset flow (navigate to dedicated screen)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: AuthViewModel,
    onNavigateToRegistration: () -> Unit,
    onNavigateToResetPassword: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    /** Local UI state for input fields (survives configuration changes). */
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    /** UI state exposed by the ViewModel. */
    val state = viewModel.uiState

    /** Snackbar host to show one-off messages (errors, confirmations). */
    val snackbarHostState = remember { SnackbarHostState() }

    /** Coroutine scope for showing snackbars from button callbacks. */
    val scope = rememberCoroutineScope()

    // Show error in a snackbar and clear it in the ViewModel.
    LaunchedEffect(state.error) {
        val err = state.error
        if (!err.isNullOrBlank()) {
            snackbarHostState.showSnackbar(err)
            viewModel.clearError()
        }
    }

    // Show informational message in a snackbar and clear it in the ViewModel.
    LaunchedEffect(state.message) {
        val msg = state.message
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    // Navigate after a successful login.
    LaunchedEffect(state.isAuthenticated) {
        if (state.isAuthenticated) {
            onLoginSuccess()
        }
    }

    /** Used to conditionally display the "Resend verification email" action. */
    val isEmailVerificationProblem =
        (state.error ?: "").contains("verification", ignoreCase = true)

    Scaffold(
        topBar = { TopAppBar(title = { Text("SafetYSec") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Login to SafetYSec", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(32.dp))

            // Email input
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(Modifier.height(16.dp))

            // Password input
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (!state.isLoading && email.isNotBlank() && password.isNotBlank()) {
                            viewModel.login(email, password)
                        } else if (email.isBlank() || password.isBlank()) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Enter email and password.")
                            }
                        }
                    }
                )
            )

            Spacer(Modifier.height(24.dp))

            // Login button
            Button(
                onClick = { viewModel.login(email, password) },
                enabled = !state.isLoading && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .height(18.dp)
                            .padding(end = 12.dp)
                    )
                    Text("Logging in...")
                } else {
                    Text("Login")
                }
            }

            // MFA helper: if email is not verified, allow resending the verification email.
            if (isEmailVerificationProblem) {
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = { viewModel.resendVerificationEmail() },
                    enabled = !state.isLoading
                ) {
                    Text("Resend verification email")
                }
            }

            Spacer(Modifier.height(20.dp))

            // Password reset: navigate to dedicated reset screen.
            TextButton(
                onClick = onNavigateToResetPassword,
                enabled = !state.isLoading
            ) {
                Text("Forgot password?")
            }

            Spacer(Modifier.height(8.dp))

            // Navigate to registration screen.
            TextButton(
                onClick = onNavigateToRegistration,
                enabled = !state.isLoading
            ) {
                Text("Don't have an account? Register")
            }
        }
    }
}
