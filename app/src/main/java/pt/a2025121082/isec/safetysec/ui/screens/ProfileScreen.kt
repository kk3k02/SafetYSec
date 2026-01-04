package pt.a2025121082.isec.safetysec.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import pt.a2025121082.isec.safetysec.viewmodel.AuthViewModel

/**
 * Screen for managing the user profile.
 * 
 * Features:
 * - View and edit display name.
 * - View and edit email address (requires current password for re-authentication).
 * - Change account password (requires current password and confirmation).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState
    val scrollState = rememberScrollState()

    // Form states for profile information
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    
    // State for re-authentication password during email change
    var currentPasswordForProfile by remember { mutableStateOf("") }

    // States for the password change form
    var currentPasswordForPwdChange by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }

    // Visibility control for dialogs
    var showEmailChangeDialog by remember { mutableStateOf(false) }
    var showPasswordChangeDialog by remember { mutableStateOf(false) }

    // Sync local form states with data from the ViewModel when it becomes available
    LaunchedEffect(uiState.accountName, uiState.accountEmail) {
        if (name.isEmpty() && uiState.accountName != null) {
            name = uiState.accountName
        }
        if (email.isEmpty() && uiState.accountEmail != null) {
            email = uiState.accountEmail
        }
    }

    // Trigger data loading when the screen is first displayed
    LaunchedEffect(Unit) {
        viewModel.loadAccountInfo()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        @Suppress("DEPRECATION")
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Display feedback messages from the ViewModel (Errors/Success)
            if (uiState.error != null) {
                Text(
                    text = uiState.error,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            if (uiState.message != null) {
                Text(
                    text = uiState.message,
                    color = Color(0xFF4CAF50), // Standard Green for success
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // --- Profile Information Section ---
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "User Information",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) }
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            // If email changed, we need a password confirmation dialog first
                            if (email != uiState.accountEmail) {
                                showEmailChangeDialog = true
                            } else {
                                // Otherwise, just update the name
                                viewModel.updateProfile(name, email, null)
                            }
                        },
                        modifier = Modifier.align(Alignment.End),
                        enabled = !uiState.isLoading
                    ) {
                        Text("Update Profile")
                    }
                }
            }

            // --- Security / Change Password Section ---
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Security",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { showPasswordChangeDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Change Password")
                    }
                }
            }
        }

        // --- Overlay Dialogs ---

        // Email Change Confirmation Dialog
        // Changing email in Firebase is a sensitive operation and requires recent re-authentication
        if (showEmailChangeDialog) {
            AlertDialog(
                onDismissRequest = { showEmailChangeDialog = false },
                title = { Text("Confirm Email Change") },
                text = {
                    Column {
                        Text("Changing your email address requires re-authentication. Please enter your current password to proceed.")
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = currentPasswordForProfile,
                            onValueChange = { currentPasswordForProfile = it },
                            label = { Text("Current Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.updateProfile(name, email, currentPasswordForProfile)
                            showEmailChangeDialog = false
                            currentPasswordForProfile = ""
                        }
                    ) {
                        Text("Confirm")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEmailChangeDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Password Change Dialog
        // Collects current password and new password (with confirmation)
        if (showPasswordChangeDialog) {
            AlertDialog(
                onDismissRequest = { showPasswordChangeDialog = false },
                title = { Text("Change Password") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = currentPasswordForPwdChange,
                            onValueChange = { currentPasswordForPwdChange = it },
                            label = { Text("Current Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = newPassword,
                            onValueChange = { newPassword = it },
                            label = { Text("New Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = confirmNewPassword,
                            onValueChange = { confirmNewPassword = it },
                            label = { Text("Confirm New Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (newPassword == confirmNewPassword) {
                                viewModel.changePassword(currentPasswordForPwdChange, newPassword)
                                showPasswordChangeDialog = false
                                // Clear temporary password states for security
                                currentPasswordForPwdChange = ""
                                newPassword = ""
                                confirmNewPassword = ""
                            }
                        }
                    ) {
                        Text("Change")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPasswordChangeDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}
