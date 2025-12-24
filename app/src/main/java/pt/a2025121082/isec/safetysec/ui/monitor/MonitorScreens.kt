package pt.a2025121082.isec.safetysec.ui.monitor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import pt.a2025121082.isec.safetysec.data.model.RuleParams
import pt.a2025121082.isec.safetysec.data.model.RuleType
import pt.a2025121082.isec.safetysec.viewmodel.AppViewModel
import pt.a2025121082.isec.safetysec.viewmodel.AuthViewModel

/**
 * Monitor dashboard screen (placeholder).
 *
 * In the future this can show:
 * - recent alerts
 * - status of protected users
 * - monitoring statistics
 */
@Composable
fun MonitorDashboardScreen(vm: AppViewModel) {
    Column(Modifier.padding(16.dp)) {
        Text("Monitor dashboard")
        Spacer(Modifier.height(8.dp))
        Text("TODO: recent alerts, status of protected users, statistics.")
    }
}

/**
 * Screen used by a Monitor to link with a Protected user using an OTP association code.
 */
@Composable
fun MonitorLinkScreen(vm: AppViewModel) {
    val st = vm.state
    var code by remember { mutableStateOf("") }

    Column(Modifier.padding(16.dp)) {
        Text("Link with Protected (OTP)")
        Spacer(Modifier.height(8.dp))

        // OTP input
        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("Association code") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        // Submit association request
        Button(
            onClick = { vm.linkWithOtp(code) },
            enabled = !st.isLoading && code.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Link") }

        // Inline error
        st.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

/**
 * Screen used by a Monitor to request monitoring rules for a specific Protected user.
 *
 * NOTE: This currently uses Protected UID input directly (simple prototype).
 * In a real app, you would show a list of linked Protected users instead.
 */
@Composable
fun MonitorRulesScreen(vm: AppViewModel) {
    val st = vm.state
    var protectedId by remember { mutableStateOf("") }

    // Rule toggles
    var fall by remember { mutableStateOf(true) }
    var accident by remember { mutableStateOf(false) }
    var geofence by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(false) }
    var inactivity by remember { mutableStateOf(false) }
    var panic by remember { mutableStateOf(true) }

    // Rule parameters (optional)
    var maxSpeed by remember { mutableStateOf("") }
    var inactMin by remember { mutableStateOf("") }

    Column(Modifier.padding(16.dp)) {
        Text("Request rules for Protected")
        Spacer(Modifier.height(8.dp))

        // Protected user identifier
        OutlinedTextField(
            value = protectedId,
            onValueChange = { protectedId = it },
            label = { Text("Protected UID") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        // Rule selection toggles
        RuleToggle("Fall", fall) { fall = it }
        RuleToggle("Accident", accident) { accident = it }
        RuleToggle("Geofence", geofence) { geofence = it }
        RuleToggle("Speed", speed) { speed = it }
        RuleToggle("Inactivity", inactivity) { inactivity = it }
        RuleToggle("Panic", panic) { panic = it }

        Spacer(Modifier.height(8.dp))

        // Parameter inputs enabled only when the corresponding rule is selected
        OutlinedTextField(
            value = maxSpeed,
            onValueChange = { maxSpeed = it },
            label = { Text("Max speed (km/h)") },
            enabled = speed,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = inactMin,
            onValueChange = { inactMin = it },
            label = { Text("Inactivity minutes") },
            enabled = inactivity,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        // Send request to backend (Firestore)
        Button(
            onClick = {
                vm.requestRulesForProtected(
                    protectedUid = protectedId.trim(),
                    enabledTypes = buildList {
                        if (fall) add(RuleType.FALL)
                        if (accident) add(RuleType.ACCIDENT)
                        if (geofence) add(RuleType.GEOFENCE)
                        if (speed) add(RuleType.SPEED)
                        if (inactivity) add(RuleType.INACTIVITY)
                        if (panic) add(RuleType.PANIC)
                    },
                    params = RuleParams(
                        maxSpeed = maxSpeed.toFloatOrNull(),
                        inactivityDurationMin = inactMin.toIntOrNull(),
                        geofenceAreas = null // TODO: map UI to geofenceAreas
                    )
                )
            },
            enabled = protectedId.isNotBlank() && !st.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Send request") }

        // Inline error
        st.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

/**
 * Reusable row for enabling/disabling a rule in the UI.
 */
@Composable
private fun RuleToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/**
 * Profile screen for the Monitor flow.
 *
 * Shows basic account info and provides an action to switch to the Protected UI.
 */
@Composable
fun MonitorProfileScreen(
    onSwitchToProtected: () -> Unit,
    authVm: AuthViewModel = hiltViewModel()
) {
    val authSt = authVm.uiState

    // Load account info when this screen is first composed.
    LaunchedEffect(Unit) {
        authVm.loadAccountInfo()
    }

    Column(Modifier.padding(16.dp)) {
        Text("Monitor profile")
        Spacer(Modifier.height(8.dp))

        // Account info visible in Profile tab
        Text("Account info", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text("Name: ${authSt.accountName ?: "-"}")
        Text("Email: ${authSt.accountEmail ?: "-"}")

        Spacer(Modifier.height(12.dp))
        Divider()
        Spacer(Modifier.height(12.dp))

        // Switch role/UI
        Button(onClick = onSwitchToProtected, modifier = Modifier.fillMaxWidth()) {
            Text("Switch to Protected UI")
        }

        // Inline status messages
        authSt.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        authSt.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}