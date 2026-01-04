package pt.a2025121082.isec.safetysec.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import pt.a2025121082.isec.safetysec.viewmodel.AuthViewModel

/**
 * Screen that allows users to request a password reset email.
 *
 * Responsibilities:
 * - Collect the user's email address.
 * - Trigger the password reset process via [AuthViewModel].
 * - Provide feedback to the user (loading states, errors, success messages).
 * - Navigate back to the login screen.
 */
@Composable
fun PasswordResetScreen(
    authViewModel: AuthViewModel,
    onDone: () -> Unit
) {
    /** Local UI state for the email input field. */
    var email by remember { mutableStateOf("") }

    /** Observe the authentication state from the ViewModel. */
    val state = authViewModel.uiState

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text = "Reset Password",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(Modifier.height(8.dp))
        
        Text(
            text = "Enter your email address to receive instructions on how to reset your password.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        // Display error messages from the ViewModel if any
        if (state.error != null) {
            Text(
                text = state.error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Display success messages (e.g., "Reset link sent")
        if (state.message != null) {
            Text(
                text = state.message,
                color = Color(0xFF4CAF50), // Green color for success feedback
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Email input field
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email Address") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading,
            singleLine = true
        )

        Spacer(Modifier.height(24.dp))

        // Button to trigger the password reset email
        Button(
            onClick = { authViewModel.sendPasswordResetEmail(email) },
            enabled = !state.isLoading && email.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Send Reset Link")
            }
        }

        Spacer(Modifier.height(16.dp))

        // Navigation back to the previous screen (usually Login)
        TextButton(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back to Login")
        }
    }
}
