package pt.a2025121082.isec.safetysec.ui.monitor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import pt.a2025121082.isec.safetysec.data.model.*
import pt.a2025121082.isec.safetysec.viewmodel.AppViewModel
import pt.a2025121082.isec.safetysec.viewmodel.AuthViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Monitor dashboard screen.
 */
@Composable
fun MonitorDashboardScreen(vm: AppViewModel) {
    val state = vm.state
    val sdf = remember { SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Dashboard Statistics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard("Protected", state.linkedProtectedUsers.size.toString(), Modifier.weight(1f))
                StatCard("Recent Alerts", state.monitorAlerts.size.toString(), Modifier.weight(1f))
            }
        }

        item {
            Text("Protected Users Status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (state.linkedProtectedUsers.isEmpty()) {
            item {
                Text("No linked users.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
        } else {
            items(state.linkedProtectedUsers) { user ->
                ProtectedUserStatusCard(user)
            }
        }

        item {
            Text("Recent Alerts", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (state.monitorAlerts.isEmpty()) {
            item {
                Text("No recent alerts.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
            }
        } else {
            items(state.monitorAlerts) { alert ->
                AlertItem(alert, sdf)
            }
        }
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun ProtectedUserStatusCard(user: User) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text(user.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Roles: ${user.roles.joinToString(", ")}", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun AlertItem(alert: Alert, sdf: SimpleDateFormat) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1F1))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${alert.type.displayName()} ALERT",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.Red,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.weight(1f))
                Text(sdf.format(Date(alert.timestamp)), style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(4.dp))
            Text("User: ${alert.protectedName}", style = MaterialTheme.typography.bodyMedium)
            if (alert.location != null) {
                Text(
                    "Location: ${alert.location.latitude}, ${alert.location.longitude}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun MonitorLinkScreen(vm: AppViewModel) {
    val st = vm.state
    var code by remember { mutableStateOf("") }

    Column(Modifier.padding(16.dp)) {
        Text("Link with Protected (OTP)", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = code,
            onValueChange = { code = it },
            label = { Text("6-digit code") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { vm.linkWithOtp(code) },
            enabled = !st.isLoading && code.length == 6,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Link Account") }

        st.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
    }
}

/**
 * Monitor rules configuration screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorRulesScreen(vm: AppViewModel) {
    val st = vm.state
    
    // UI selection state
    var expanded by remember { mutableStateOf(false) }
    var selectedUser by remember { mutableStateOf<User?>(null) }

    // Rule toggles
    var fall by remember { mutableStateOf(true) }
    var accident by remember { mutableStateOf(false) }
    var geofence by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(false) }
    var inactivity by remember { mutableStateOf(false) }
    var panic by remember { mutableStateOf(true) }

    // Parameters
    var maxSpeed by remember { mutableStateOf("") }
    var inactMin by remember { mutableStateOf("") }
    var geoLat by remember { mutableStateOf("") }
    var geoLng by remember { mutableStateOf("") }
    var geoRadius by remember { mutableStateOf("") }

    // Refresh bundle when user selection changes
    LaunchedEffect(selectedUser) {
        selectedUser?.let { vm.loadRulesForProtected(it.uid) }
    }

    LazyColumn(Modifier.padding(16.dp)) {
        item {
            Text("Monitoring Configuration", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            
            // --- Protected User Selector ---
            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded }
            ) {
                OutlinedTextField(
                    value = selectedUser?.name ?: "Select Protected User",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Protected User") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    st.linkedProtectedUsers.forEach { user ->
                        DropdownMenuItem(
                            text = { Text(user.name) },
                            onClick = {
                                selectedUser = user
                                expanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (selectedUser != null) {
            // --- Display current authorization status ---
            item {
                st.rulesForSelectedProtected?.let { bundle ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Agreement Status", fontWeight = FontWeight.Bold)
                            Text(
                                "User has authorized: ${bundle.authorizedTypes.joinToString { it.displayName() }.ifEmpty { "None yet" }}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // --- Rule Toggles ---
            item { RuleToggle("Fall Detection", fall) { fall = it } }
            item { RuleToggle("Accident Detection", accident) { accident = it } }
            item { RuleToggle("Geofencing", geofence) { geofence = it } }
            item { RuleToggle("Speed Monitoring", speed) { speed = it } }
            item { RuleToggle("Inactivity Tracking", inactivity) { inactivity = it } }
            item { RuleToggle("Panic Button", panic) { panic = it } }

            // --- Parameters Section ---
            item {
                Spacer(Modifier.height(16.dp))
                if (speed) {
                    OutlinedTextField(
                        value = maxSpeed,
                        onValueChange = { maxSpeed = it },
                        label = { Text("Max speed (km/h)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (inactivity) {
                    OutlinedTextField(
                        value = inactMin,
                        onValueChange = { inactMin = it },
                        label = { Text("Inactivity minutes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (geofence) {
                    Text("Geofence Area", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = geoLat, onValueChange = { geoLat = it }, label = { Text("Lat") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = geoLng, onValueChange = { geoLng = it }, label = { Text("Lng") }, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = geoRadius, onValueChange = { geoRadius = it }, label = { Text("Radius (meters)") }, modifier = Modifier.fillMaxWidth())
                }

                Spacer(Modifier.height(24.dp))
                
                Button(
                    onClick = {
                        val lat = geoLat.toDoubleOrNull() ?: 0.0
                        val lng = geoLng.toDoubleOrNull() ?: 0.0
                        val rad = geoRadius.toDoubleOrNull() ?: 0.0
                        
                        vm.requestRulesForProtected(
                            protectedUid = selectedUser!!.uid,
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
                                geofenceAreas = if (geofence) listOf(GeofenceArea(lat, lng, rad)) else null
                            )
                        )
                    },
                    enabled = !st.isLoading,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Update Configuration Request") }

                st.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }
        } else {
            item {
                Text("Please select a linked protected user to configure rules.", color = Color.Gray)
            }
        }
    }
}

@Composable
private fun RuleToggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun MonitorProfileScreen(
    onSwitchToProtected: () -> Unit,
    authVm: AuthViewModel = hiltViewModel()
) {
    val authSt = authVm.uiState

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
        Button(
            onClick = onSwitchToProtected,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
        ) {
            Text("Switch to Protected Mode")
        }

        authSt.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        authSt.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
    }
}
