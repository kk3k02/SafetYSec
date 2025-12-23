package pt.a2025121082.isec.safetysec.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import pt.a2025121082.isec.safetysec.viewmodel.AuthViewModel

@Composable
fun PasswordResetScreen(
    authViewModel: AuthViewModel,
    onDone: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    val state = authViewModel.uiState

    Column(Modifier.padding(16.dp)) {
        Text("Password reset")

        Spacer(Modifier.padding(8.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.padding(8.dp))

        Button(
            onClick = { authViewModel.sendPasswordResetEmail(email) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isLoading
        ) {
            Text("Send reset email")
        }

        state.message?.let {
            Spacer(Modifier.padding(8.dp))
            Text(it)
        }

        state.error?.let {
            Spacer(Modifier.padding(8.dp))
            Text(it)
        }

        Spacer(Modifier.padding(12.dp))

        TextButton(onClick = onDone) {
            Text("Back")
        }
    }
}
