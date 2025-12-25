package pt.a2025121082.isec.safetysec.ui.protected

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import pt.a2025121082.isec.safetysec.data.model.RuleType
import pt.a2025121082.isec.safetysec.data.model.User
import pt.a2025121082.isec.safetysec.viewmodel.AppViewModel
import pt.a2025121082.isec.safetysec.viewmodel.AuthViewModel

/**
 * Protected history screen.
 */
@Composable
fun ProtectedHistoryScreen(vm: AppViewModel) {
    val st = vm.state
    Column(Modifier.padding(16.dp)) {
        Text("History (Protected)", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        LazyColumn {
            items(st.myAlerts) { a ->
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    androidx.compose.material3.ListItem(
                        headlineContent = { Text(a.type.displayName()) },
                        supportingContent = { Text("Protected=${a.protectedName} • ${a.timestamp}") }
                    )
                }
            }
        }
    }
}

/**
 * Screen for managing time windows when monitoring rules are allowed to be active.
 */
@Composable
fun ProtectedWindowsScreen(vm: AppViewModel) {
    val st = vm.state
    var days by remember { mutableStateOf("1,2,3,4,5") }
    var start by remember { mutableStateOf("9") }
    var end by remember { mutableStateOf("18") }

    Column(Modifier.padding(16.dp)) {
        Text("Time windows", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        // Existing windows
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(st.timeWindows) { w ->
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    androidx.compose.material3.ListItem(
                        headlineContent = { Text("Days: ${w.daysOfWeek}") },
                        supportingContent = { Text("${w.startHour}:00 - ${w.endHour}:00") }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Add new window (simple prototype UI)
        Text("Add window", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = days,
            onValueChange = { days = it },
            label = { Text("Days (1..7 comma)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = start,
            onValueChange = { start = it },
            label = { Text("Start hour (0..23)") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = end,
            onValueChange = { end = it },
            label = { Text("End hour (0..23)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val parsedDays = days
                    .split(",")
                    .mapNotNull { it.trim().toIntOrNull() }
                    .filter { it in 1..7 }

                vm.addTimeWindow(
                    parsedDays,
                    start.toIntOrNull() ?: 0,
                    end.toIntOrNull() ?: 0
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save") }
    }
}

/**
 * Screen for managing linked Monitors and authorizing monitoring permissions.
 */
@Composable
fun ProtectedMonitorsAndRulesScreen(vm: AppViewModel) {
    val st = vm.state
    var showOtpDialog by remember { mutableStateOf(false) }
    var showRemovalSuccessDialog by remember { mutableStateOf(false) }

    // State for Pending Request Dialog
    var pendingRequestMonitor by remember { mutableStateOf<Pair<User, List<RuleType>>?>(null) }

    // Observe removal success
    LaunchedEffect(st.isRemovalSuccessful) {
        if (st.isRemovalSuccessful) {
            showRemovalSuccessDialog = true
        }
    }

    // Detect if any monitor has a "requested" list that isn't fully in "authorizedTypes"
    LaunchedEffect(st.monitorRuleBundles, st.myLinkedMonitors) {
        st.myLinkedMonitors.forEach { monitor ->
            val bundle = st.monitorRuleBundles.find { it.monitorId == monitor.uid }
            if (bundle != null && bundle.requested.isNotEmpty()) {
                val requestedTypes = bundle.requested.map { it.type }
                val notYetAuthorized = requestedTypes.filter { !bundle.authorizedTypes.contains(it) }
                if (notYetAuthorized.isNotEmpty()) {
                    pendingRequestMonitor = monitor to requestedTypes
                }
            }
        }
    }

    LaunchedEffect(st.myOtp) {
        if (st.myOtp != null) {
            showOtpDialog = true
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Monitors & Permissions", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { vm.generateOtp() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !st.isLoading
            ) {
                Text("Generate OTP (share with Monitor)")
            }
        }

        item {
            Text("Your Monitors", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (st.myLinkedMonitors.isEmpty()) {
            item {
                Text("No monitors linked.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
        } else {
            items(st.myLinkedMonitors) { monitor ->
                val bundle = st.monitorRuleBundles.find { it.monitorId == monitor.uid }
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(monitor.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(monitor.email, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { vm.removeMonitor(monitor.uid) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove Monitor", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        
                        Divider(Modifier.padding(vertical = 12.dp))
                        Text("Grant Permissions:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)

                        val authorized = remember(monitor.uid, bundle?.authorizedTypes) {
                            mutableStateListOf<RuleType>().apply { 
                                addAll(bundle?.authorizedTypes ?: emptyList()) 
                            }
                        }

                        RuleType.entries.forEach { type ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = type.displayName(), style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = authorized.contains(type),
                                    onCheckedChange = { on ->
                                        if (on) authorized.add(type) else authorized.remove(type)
                                    }
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { vm.saveAuthorizations(monitor.uid, authorized.toList()) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Save Permissions") }
                    }
                }
            }
        }
        
        item {
            st.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        }
    }

    if (showOtpDialog && st.myOtp != null) {
        AlertDialog(
            onDismissRequest = { showOtpDialog = false },
            title = { Text("Association Code") },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Share this 6-digit code with your Monitor.")
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = st.myOtp,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Code expires in 10 minutes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showOtpDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Removal Success Dialog
    if (showRemovalSuccessDialog) {
        AlertDialog(
            onDismissRequest = { 
                showRemovalSuccessDialog = false
                vm.consumeRemovalSuccess()
            },
            title = { Text("Monitor Removed") },
            text = { Text("The monitor has been successfully unlinked from your account.") },
            confirmButton = {
                TextButton(onClick = { 
                    showRemovalSuccessDialog = false
                    vm.consumeRemovalSuccess()
                }) {
                    Text("OK")
                }
            }
        )
    }

    pendingRequestMonitor?.let { (monitor, requestedTypes) ->
        AlertDialog(
            onDismissRequest = { pendingRequestMonitor = null },
            title = { Text("New Access Request") },
            text = {
                Column {
                    Text("${monitor.name} is requesting access to the following rules:")
                    Spacer(Modifier.height(8.dp))
                    requestedTypes.forEach { type ->
                        Text("• ${type.displayName()}", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Do you want to grant these permissions?", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.saveAuthorizations(monitor.uid, requestedTypes)
                        pendingRequestMonitor = null
                    }
                ) {
                    Text("Accept All")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRequestMonitor = null }) {
                    Text("Decide Later")
                }
            }
        )
    }
}

/**
 * Protected profile screen.
 */
@Composable
fun ProtectedProfileScreen(
    vm: AppViewModel,
    onSwitchToMonitor: () -> Unit,
    authVm: AuthViewModel = hiltViewModel()
) {
    val st = vm.state
    val authSt = authVm.uiState

    var pin by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        authVm.loadAccountInfo()
    }

    Column(Modifier.padding(16.dp)) {
        Text("Account Settings", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Name: ${authSt.accountName ?: "-"}", style = MaterialTheme.typography.bodyLarge)
                Text("Email: ${authSt.accountEmail ?: "-"}", style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Security Settings", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it },
            label = { Text("Alert cancel PIN") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { vm.updateCancelPin(pin) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Update PIN")
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onSwitchToMonitor,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("Switch to Monitor Mode")
        }

        authSt.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        authSt.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
        st.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
    }
}

@Composable
fun ProtectedCancelAlertDialog(vm: AppViewModel) {
    val st = vm.state
    if (!st.isCancelWindowOpen) return

    var typed by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { },
        title = { Text("Alert triggered") },
        text = {
            Column {
                Text("You have ${st.cancelSecondsLeft}s to cancel.")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text("Enter PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = { vm.tryCancelAlert(typed) }) { Text("Cancel") }
        },
        dismissButton = {
            TextButton(onClick = {}) { Text("Wait") }
        }
    )
}
