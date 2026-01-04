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
 * Registration screen for new users.
 * 
 * Responsibilities:
 * - Collect registration details: Name, Email, and Password.
 * - Perform basic client-side validation (non-empty fields, password length).
 * - Communicate with [AuthViewModel] to handle the Firebase registration process.
 * - Provide user feedback via Snackbars for both local validation and remote errors.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(
    viewModel: AuthViewModel,
    onNavigateToLogin: () -> Unit
) {
    /** Local state for input fields using rememberSaveable to survive configuration changes like rotation. */
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }

    /** Observe the current authentication state from the ViewModel. */
    val state = viewModel.uiState

    /** State used by the Scaffold to manage Snackbar notifications. */
    val snackbarHostState = remember { SnackbarHostState() }

    /** Coroutine scope to trigger Snackbar messages from within local validation logic. */
    val scope = rememberCoroutineScope()

    // Listen for error changes in the ViewModel and display them in a Snackbar
    LaunchedEffect(state.error) {
        val err = state.error
        if (!err.isNullOrBlank()) {
            snackbarHostState.showSnackbar(err)
            viewModel.clearError()
        }
    }

    // Listen for general messages (e.g., "Verification email sent") and display them
    LaunchedEffect(state.message) {
        val msg = state.message
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    // React to successful registration by navigating back to the login screen
    LaunchedEffect(state.isRegistrationSuccessful) {
        if (state.isRegistrationSuccessful) {
            viewModel.consumeRegistrationSuccess()
            onNavigateToLogin()
        }
    }

    /**
     * Validates user input locally before calling the ViewModel registration logic.
     * Displays a snackbar if validation fails.
     */
    fun validateAndRegister() {
        val n = name.trim()
        val e = email.trim()
        val p = password

        when {
            n.isBlank() -> scope.launch { snackbarHostState.showSnackbar("Name is required.") }
            e.isBlank() -> scope.launch { snackbarHostState.showSnackbar("Email is required.") }
            p.isBlank() -> scope.launch { snackbarHostState.showSnackbar("Password is required.") }
            p.length < 6 -> scope.launch { snackbarHostState.showSnackbar("Password must be at least 6 characters.") }
            else -> viewModel.register(e, p, n)
        }
    }

    Scaffold(
        topBar = { 
            TopAppBar(title = { Text("SafetYSec") }) 
        },
        snackbarHost = { 
            SnackbarHost(snackbarHostState) 
        }
    ) { innerPadding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Create account", 
                style = MaterialTheme.typography.headlineMedium
            )
            
            Spacer(Modifier.height(32.dp))

            // User Name Input
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                )
            )

            Spacer(Modifier.height(16.dp))

            // User Email Input
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

            // User Password Input (hidden text)
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
                        if (!state.isLoading) validateAndRegister()
                    }
                )
            )

            Spacer(Modifier.height(24.dp))

            // Primary Registration Action Button
            Button(
                onClick = { validateAndRegister() },
                enabled = !state.isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (state.isLoading) {
                    // Show loading indicator during remote operation
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier
                            .height(18.dp)
                            .padding(end = 12.dp)
                    )
                    Text("Registering...")
                } else {
                    Text("Register")
                }
            }

            Spacer(Modifier.height(12.dp))

            // Option to navigate back to the Login screen
            TextButton(
                onClick = onNavigateToLogin,
                enabled = !state.isLoading
            ) {
                Text("Already have an account? Login")
            }
        }
    }
}
