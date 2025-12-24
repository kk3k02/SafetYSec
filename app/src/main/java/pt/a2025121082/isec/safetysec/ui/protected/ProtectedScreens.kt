package pt.a2025121082.isec.safetysec.ui.protected

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
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
 *
 * Displays a list of alerts received/triggered by the Protected user.
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
 * Screen for managing linked Monitors and authorizing requested monitoring rules.
 */
@Composable
fun ProtectedMonitorsAndRulesScreen(vm: AppViewModel) {
    val st = vm.state
    var showOtpDialog by remember { mutableStateOf(false) }

    // When a new OTP is generated, show the dialog automatically
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
            Text("Monitors & Authorizations", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

            // OTP generation
            Button(
                onClick = { vm.generateOtp() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !st.isLoading
            ) {
                Text("Generate OTP (share with Monitor)")
            }
        }

        // --- Linked Monitors Section ---
        item {
            Text("Your Monitors", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (st.myLinkedMonitors.isEmpty()) {
            item {
                Text("No monitors linked.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
        } else {
            items(st.myLinkedMonitors) { monitor ->
                MonitorStatusCard(monitor)
            }
        }

        // --- Rule Authorizations Section ---
        item {
            Text("Rule Requests", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (st.monitorRuleBundles.isEmpty()) {
            item {
                Text("No pending rule requests.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
        } else {
            items(st.monitorRuleBundles) { bundle ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Monitor: ${bundle.monitorId}", fontWeight = FontWeight.Bold)

                        Text("Requested rules:", style = MaterialTheme.typography.bodySmall)
                        bundle.requested.forEach { r ->
                            Text("- ${r.type.displayName()}", style = MaterialTheme.typography.bodySmall)
                        }

                        Spacer(Modifier.height(8.dp))
                        Text("Authorize:")

                        val authorized = remember(bundle.monitorId) {
                            mutableStateListOf<RuleType>().apply { addAll(bundle.authorizedTypes) }
                        }

                        RuleType.values().forEach { type ->
                            val requested = bundle.requested.any { it.type == type && it.enabled }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(type.displayName() + if (!requested) " (not requested)" else "")
                                Switch(
                                    checked = authorized.contains(type),
                                    onCheckedChange = { on ->
                                        if (on) authorized.add(type) else authorized.remove(type)
                                    },
                                    enabled = requested
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { vm.saveAuthorizations(bundle.monitorId, authorized.toList()) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Save Authorizations") }
                    }
                }
            }
        }
    }

    // OTP Display Dialog
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
}

@Composable
fun MonitorStatusCard(monitor: User) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(monitor.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(monitor.email, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * Protected profile screen.
 * Consistent UI with MonitorProfileScreen.
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

    // Load account info when this screen is first composed.
    LaunchedEffect(Unit) {
        authVm.loadAccountInfo()
    }

    Column(Modifier.padding(16.dp)) {
        Text("Account Settings", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        // Account info card
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Name: ${authSt.accountName ?: "-"}", style = MaterialTheme.typography.bodyLarge)
                Text("Email: ${authSt.accountEmail ?: "-"}", style = MaterialTheme.typography.bodyLarge)
            }
        }

        Spacer(Modifier.height(24.dp))
        Text("Security Settings", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        // Alert cancel PIN input
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

        // Show errors/messages if any
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
