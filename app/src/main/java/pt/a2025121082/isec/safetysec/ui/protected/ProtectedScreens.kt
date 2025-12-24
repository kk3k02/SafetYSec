package pt.a2025121082.isec.safetysec.ui.protected

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import pt.a2025121082.isec.safetysec.data.model.RuleType
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
        Text("History (Protected)")
        Spacer(Modifier.height(8.dp))

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
 *
 * This is currently a simplified UI that:
 * - shows existing time windows
 * - lets the user add a new window using basic text inputs
 */
@Composable
fun ProtectedWindowsScreen(vm: AppViewModel) {
    val st = vm.state
    var days by remember { mutableStateOf("1,2,3,4,5") }
    var start by remember { mutableStateOf("9") }
    var end by remember { mutableStateOf("18") }

    Column(Modifier.padding(16.dp)) {
        Text("Time windows")
        Spacer(Modifier.height(8.dp))

        // Existing windows
        LazyColumn {
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
        Text("Add window (simple)")
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

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                // Parse input like "1,2,3,4,5" and keep only valid day numbers (1..7)
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
 *
 * Features:
 * - Generates an OTP code that can be shared with a Monitor to establish an association
 * - Displays rule requests made by Monitors
 * - Allows the Protected user to authorize requested rule types per Monitor
 */
@Composable
fun ProtectedMonitorsAndRulesScreen(vm: AppViewModel) {
    val st = vm.state

    Column(Modifier.padding(16.dp)) {
        Text("Monitors & authorizations")
        Spacer(Modifier.height(8.dp))

        // OTP generation (Protected -> Monitor association)
        Button(
            onClick = { vm.generateOtp() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !st.isLoading
        ) {
            Text("Generate OTP (share with Monitor)")
        }

        // Show generated OTP
        st.myOtp?.let {
            Spacer(Modifier.height(8.dp))
            Text("OTP: $it")
        }

        Spacer(Modifier.height(12.dp))

        // Monitor rule bundles list
        LazyColumn {
            items(st.monitorRuleBundles) { bundle ->
                Card(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Monitor: ${bundle.monitorId}")

                        Text("Requested:")
                        bundle.requested.forEach { r ->
                            Text("- ${r.type.displayName()}")
                        }

                        Spacer(Modifier.height(8.dp))
                        Text("Authorize:")

                        // Local mutable list of authorized rule types for this monitor (UI state)
                        val authorized = remember(bundle.monitorId) {
                            mutableStateListOf<RuleType>().apply { addAll(bundle.authorizedTypes) }
                        }

                        // Show all rule types; toggles are enabled only if the type was requested
                        RuleType.values().forEach { type ->
                            val requested = bundle.requested.any { it.type == type && it.enabled }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
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
                        ) { Text("Save authorizations") }
                    }
                }
            }
        }
    }
}

/**
 * Protected profile screen.
 *
 * Shows account information and allows:
 * - updating the alert cancel PIN
 * - switching to the Monitor UI flow
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
        Text("Protected profile")
        Spacer(Modifier.height(8.dp))

        // Account info visible in Profile tab
        Text("Account info", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text("Name: ${authSt.accountName ?: "-"}")
        Text("Email: ${authSt.accountEmail ?: "-"}")

        Spacer(Modifier.height(12.dp))
        Divider()
        Spacer(Modifier.height(12.dp))

        // Alert cancel PIN input
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it },
            label = { Text("Alert cancel PIN") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))
        Button(onClick = { vm.updateCancelPin(pin) }, modifier = Modifier.fillMaxWidth()) {
            Text("Save PIN")
        }

        Spacer(Modifier.height(12.dp))
        Button(onClick = onSwitchToMonitor, modifier = Modifier.fillMaxWidth()) {
            Text("Switch to Monitor UI")
        }

        // Show errors/messages if any
        authSt.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        authSt.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        st.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

/**
 * Cancel dialog shown after an alert is triggered.
 *
 * Project requirement: Protected user has a 10-second window to enter a PIN and cancel the alert.
 * The dialog stays on screen until the countdown finishes or the user successfully cancels.
 */
@Composable
fun ProtectedCancelAlertDialog(vm: AppViewModel) {
    val st = vm.state
    if (!st.isCancelWindowOpen) return

    // Local state for the PIN typed by the user inside the dialog.
    var typed by remember { mutableStateOf("") }

    AlertDialog(
        // Do not allow dismiss until the time window ends (requirement).
        onDismissRequest = { /* keep until time ends */ },
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
            // Intentionally does nothing; user can wait for the timer to expire.
            TextButton(onClick = {}) { Text("Wait") }
        }
    )
}