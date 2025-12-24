package pt.a2025121082.isec.safetysec.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import pt.a2025121082.isec.safetysec.viewmodel.AuthViewModel

/**
 * Password reset screen (Jetpack Compose).
 *
 * Responsibilities:
 * - Collect the user's email address
 * - Trigger password reset email via [AuthViewModel]
 * - Provide a simple "Back" action via [onDone]
 */
@Composable
fun PasswordResetScreen(
    authViewModel: AuthViewModel,
    onDone: () -> Unit
) {
    /** Local UI state for the email input. */
    var email by remember { mutableStateOf("") }

    /** UI state exposed by the ViewModel (loading/error/message). */
    val state = authViewModel.uiState

    Column(Modifier.padding(16.dp)) {
        Text("Reset password")
        Spacer(Modifier.padding(8.dp))

        // Email input
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.padding(8.dp))

        // Send reset email action
        Button(
            onClick = { authViewModel.sendPasswordResetEmail(email) },
            enabled = !state.isLoading && email.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Send reset email")
        }

        Spacer(Modifier.padding(8.dp))

        // Navigate back to previous screen
        TextButton(onClick = onDone) { Text("Back") }
    }
}