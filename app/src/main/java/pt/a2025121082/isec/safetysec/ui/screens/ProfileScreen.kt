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
 * Screen for managing the user profile:
 * - View/Edit name
 * - View/Edit email (requires current password for re-authentication)
 * - Change password (requires current password)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onNavigateBack: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState
    val scrollState = rememberScrollState()

    // Form states
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var currentPasswordForProfile by remember { mutableStateOf("") }

    var currentPasswordForPwdChange by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmNewPassword by remember { mutableStateOf("") }

    // Dialog control
    var showEmailChangeDialog by remember { mutableStateOf(false) }
    var showPasswordChangeDialog by remember { mutableStateOf(false) }

    // Initialize fields when account info is loaded
    LaunchedEffect(uiState.accountName, uiState.accountEmail) {
        if (name.isEmpty() && uiState.accountName != null) {
            name = uiState.accountName
        }
        if (email.isEmpty() && uiState.accountEmail != null) {
            email = uiState.accountEmail
        }
    }

    // Refresh data on entry
    LaunchedEffect(Unit) {
        viewModel.loadAccountInfo()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
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
            // Error/Message Display
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
                    color = Color(0xFF4CAF50), // Green
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
                            if (email != uiState.accountEmail) {
                                showEmailChangeDialog = true
                            } else {
                                viewModel.updateProfile(name, email, null)
                            }
                        },
                        modifier = Modifier.align(Alignment.End),
                        enabled = !uiState.isLoading
                    ) {
                        Text("Update Profile")
                    }
                }
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
    }

    // --- Dialogs ---

    // Email Change Confirmation Dialog (Requires current password)
    if (showEmailChangeDialog) {
        AlertDialog(
            onDismissRequest = { showEmailChangeDialog = false },
            title = { Text("Confirm Email Change") },
            text = {
                Column {
                    Text("Changing your email address requires re-authentication. Please enter your current password.")
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
                            currentPasswordForPwdChange = ""
                            newPassword = ""
                            confirmNewPassword = ""
                        } else {
                            // Local error check could be added here
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
}}
