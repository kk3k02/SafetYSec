package pt.a2025121082.isec.safetysec.ui.monitor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import pt.a2025121082.isec.safetysec.data.model.*
import pt.a2025121082.isec.safetysec.data.repository.MonitorRulesBundle
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
    var showRemovalSuccessDialog by remember { mutableStateOf(false) }

    // Observe removal success
    LaunchedEffect(state.isRemovalSuccessful) {
        if (state.isRemovalSuccessful) {
            showRemovalSuccessDialog = true
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. Statistics Section ---
        item {
            Text("Dashboard Overview", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard("Monitored Users", state.linkedProtectedUsers.size.toString(), Icons.Default.People, Modifier.weight(1f))
                StatCard("Total Alerts", state.monitorAlerts.size.toString(), Icons.Default.NotificationsActive, Modifier.weight(1f))
            }
        }

        // --- 2. Linked Protected Users Section ---
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Monitored Protected Users", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        if (state.linkedProtectedUsers.isEmpty()) {
            item {
                EmptyStateCard("No users assigned to your account yet.")
            }
        } else {
            items(state.linkedProtectedUsers) { user ->
                ProtectedUserStatusCard(user, onRemove = { vm.removeProtectedUser(user.uid) })
            }
        }

        // --- 3. Recent Activity Section ---
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
                Text("Recent Activity Log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
        if (state.monitorAlerts.isEmpty()) {
            item {
                EmptyStateCard("No alerts recorded recently.")
            }
        } else {
            items(state.monitorAlerts) { alert ->
                AlertItem(alert, sdf)
            }
        }
        
        item {
            state.error?.let { 
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) 
            }
        }
    }

    // Removal Success Dialog
    if (showRemovalSuccessDialog) {
        AlertDialog(
            onDismissRequest = { 
                showRemovalSuccessDialog = false
                vm.consumeRemovalSuccess()
            },
            title = { Text("User Unlinked") },
            text = { Text("The protected user has been successfully unlinked from your dashboard.") },
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
}

@Composable
fun StatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable
fun EmptyStateCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.5f))
    ) {
        Box(Modifier.padding(24.dp), contentAlignment = Alignment.Center) {
            Text(message, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        }
    }
}

@Composable
fun ProtectedUserStatusCard(user: User, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(user.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(user.email, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Unlink User", tint = MaterialTheme.colorScheme.error)
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
            Text("User: ${alert.protectedName}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (alert.location != null) {
                Text(
                    "Location: ${String.format("%.5f", alert.location.latitude)}, ${String.format("%.5f", alert.location.longitude)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.DarkGray
                )
            }
        }
    }
}

@Composable
fun MonitorLinkScreen(vm: AppViewModel) {
    val st = vm.state
    var code by remember { mutableStateOf("") }
    var showSuccessDialog by remember { mutableStateOf(false) }

    LaunchedEffect(st.isLinkingSuccessful) {
        if (st.isLinkingSuccessful) {
            showSuccessDialog = true
        }
    }

    Column(Modifier.padding(16.dp)) {
        Text("Link with Protected (OTP)", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = code,
            onValueChange = { if (it.length <= 6) code = it },
            label = { Text("6-digit code") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { vm.linkWithOtp(code) },
            enabled = !st.isLoading && code.length == 6,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Link Account") }

        st.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
    }

    if (showSuccessDialog) {
        AlertDialog(
            onDismissRequest = { 
                showSuccessDialog = false
                vm.consumeLinkingSuccess()
            },
            title = { Text("Success") },
            text = { Text("Protected user successfully linked!") },
            confirmButton = {
                TextButton(onClick = { 
                    showSuccessDialog = false
                    vm.consumeLinkingSuccess()
                    code = "" 
                }) {
                    Text("OK")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorRulesScreen(vm: AppViewModel) {
    val st = vm.state
    
    var expanded by remember { mutableStateOf(false) }
    var selectedUser by remember { mutableStateOf<User?>(null) }
    var showRequestDialog by remember { mutableStateOf(false) }
    var showRequestSuccessDialog by remember { mutableStateOf(false) }

    LaunchedEffect(st.isRequestSuccessful) {
        if (st.isRequestSuccessful) {
            showRequestSuccessDialog = true
        }
    }

    LaunchedEffect(selectedUser) {
        selectedUser?.let { vm.loadRulesForProtected(it.uid) }
    }

    LazyColumn(Modifier.padding(16.dp)) {
        item {
            Text("Monitoring Configuration", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            
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
            item {
                st.rulesForSelectedProtected?.let { bundle ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Current Authorizations", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Green = Authorized, Red = Denied", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Spacer(Modifier.height(8.dp))
                            
                            RuleType.entries.forEach { type ->
                                val isAuth = bundle.authorizedTypes.contains(type)
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = type.displayName(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Unspecified
                                    )
                                    Switch(
                                        checked = isAuth,
                                        onCheckedChange = null,
                                        enabled = false,
                                        colors = SwitchDefaults.colors(
                                            disabledCheckedThumbColor = Color.White,
                                            disabledCheckedTrackColor = Color(0xFF2E7D32),
                                            disabledUncheckedThumbColor = Color.White,
                                            disabledUncheckedTrackColor = Color(0xFFD32F2F),
                                            disabledUncheckedBorderColor = Color(0xFFD32F2F)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = { showRequestDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Request New Configuration")
                }
                
                st.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }
        } else {
            item {
                Text("Please select a linked protected user to view rules.", color = Color.Gray)
            }
        }
    }

    if (showRequestDialog && selectedUser != null) {
        RequestRulesDialog(
            user = selectedUser!!,
            onDismiss = { showRequestDialog = false },
            onSend = { types, params ->
                vm.requestRulesForProtected(selectedUser!!.uid, types, params)
                showRequestDialog = false
            }
        )
    }

    if (showRequestSuccessDialog) {
        AlertDialog(
            onDismissRequest = { 
                showRequestSuccessDialog = false
                vm.consumeRequestSuccess()
            },
            title = { Text("Request Sent") },
            text = { Text("The monitoring configuration request has been sent to the protected user.") },
            confirmButton = {
                TextButton(onClick = { 
                    showRequestSuccessDialog = false
                    vm.consumeRequestSuccess()
                }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun RequestRulesDialog(
    user: User,
    onDismiss: () -> Unit,
    onSend: (List<RuleType>, RuleParams) -> Unit
) {
    var fall by remember { mutableStateOf(false) }
    var accident by remember { mutableStateOf(false) }
    var geofence by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(false) }
    var inactivity by remember { mutableStateOf(false) }
    var panic by remember { mutableStateOf(false) }

    var maxSpeed by remember { mutableStateOf("") }
    var inactMin by remember { mutableStateOf("") }
    var geoLat by remember { mutableStateOf("") }
    var geoLng by remember { mutableStateOf("") }
    var geoRadius by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Request Rules for ${user.name}") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Select rules you want to monitor:", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(8.dp))

                RuleToggle("Fall Detection", fall) { fall = it }
                RuleToggle("Accident Detection", accident) { accident = it }
                RuleToggle("Geofencing", geofence) { geofence = it }
                RuleToggle("Speed Monitoring", speed) { speed = it }
                RuleToggle("Inactivity Tracking", inactivity) { inactivity = it }
                RuleToggle("Panic Button", panic) { panic = it }

                Spacer(Modifier.height(16.dp))
                if (speed) {
                    OutlinedTextField(
                        value = maxSpeed,
                        onValueChange = { maxSpeed = it },
                        label = { Text("Max speed (km/h)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                if (inactivity) {
                    OutlinedTextField(
                        value = inactMin,
                        onValueChange = { inactMin = it },
                        label = { Text("Inactivity minutes") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                if (geofence) {
                    Text("Geofence Area", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = geoLat,
                            onValueChange = { geoLat = it },
                            label = { Text("Lat") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = geoLng,
                            onValueChange = { geoLng = it },
                            label = { Text("Lng") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    OutlinedTextField(
                        value = geoRadius,
                        onValueChange = { geoRadius = it },
                        label = { Text("Radius (meters)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val types = buildList {
                    if (fall) add(RuleType.FALL)
                    if (accident) add(RuleType.ACCIDENT)
                    if (geofence) add(RuleType.GEOFENCE)
                    if (speed) add(RuleType.SPEED)
                    if (inactivity) add(RuleType.INACTIVITY)
                    if (panic) add(RuleType.PANIC)
                }
                val params = RuleParams(
                    maxSpeed = maxSpeed.toFloatOrNull(),
                    inactivityDurationMin = inactMin.toIntOrNull(),
                    geofenceAreas = if (geofence) listOf(
                        GeofenceArea(
                            geoLat.toDoubleOrNull() ?: 0.0,
                            geoLng.toDoubleOrNull() ?: 0.0,
                            geoRadius.toDoubleOrNull() ?: 0.0
                        )
                    ) else null
                )
                onSend(types, params)
            }) {
                Text("Send Request")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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
        Text(label, style = MaterialTheme.typography.bodyMedium)
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
