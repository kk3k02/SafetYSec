package pt.a2025121082.isec.safetysec.ui.monitor

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MenuAnchorType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import pt.a2025121082.isec.safetysec.data.model.*
import pt.a2025121082.isec.safetysec.data.repository.MonitorRulesBundle
import pt.a2025121082.isec.safetysec.ui.components.VideoPlayer
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
    var showClearedDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.isRemovalSuccessful) {
        if (state.isRemovalSuccessful) {
            showRemovalSuccessDialog = true
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatCard("Monitored Users", state.linkedProtectedUsers.size.toString(), Icons.Default.People, Modifier.weight(1f))
                StatCard("Recent Alerts", state.monitorAlerts.size.toString(), Icons.Default.NotificationsActive, Modifier.weight(1f))
            }
        }

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

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
                Text("Recent Activity Log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = {
                        vm.clearMonitorAlertsHistory()
                        showClearedDialog = true
                    },
                    enabled = state.monitorAlerts.isNotEmpty()
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear recent alerts")
                }
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

    if (showClearedDialog) {
        AlertDialog(
            onDismissRequest = { showClearedDialog = false },
            title = { Text("Recent alerts cleared") },
            text = { Text("The recent alerts list has been cleared.") },
            confirmButton = {
                TextButton(onClick = { showClearedDialog = false }) {
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
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
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
    var showVideo by remember { mutableStateOf(false) }

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

            val videoUrl = alert.videoUrl
            if (!videoUrl.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showVideo = !showVideo },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                ) {
                    Icon(if (showVideo) Icons.Default.VisibilityOff else Icons.Default.PlayCircle, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (showVideo) "Hide Video Evidence" else "View Evidence Video")
                }

                AnimatedVisibility(visible = showVideo) {
                    Box(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .height(250.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        VideoPlayer(videoUrl = videoUrl)
                    }
                }
            }
        }
    }
}

@Composable
fun MonitorLinkScreen(vm: AppViewModel) {
    val st = vm.state
    val digits = remember { mutableStateListOf("", "", "", "", "", "") }
    var showSuccessDialog by remember { mutableStateOf(false) }
    var showErrorDialog by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val focusRequesters = remember { List(6) { FocusRequester() } }

    LaunchedEffect(st.isLinkingSuccessful) {
        if (st.isLinkingSuccessful) {
            showSuccessDialog = true
        }
    }

    LaunchedEffect(st.linkError) {
        if (st.linkError != null) {
            showErrorDialog = true
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Enter the 6-digit code from the Protected user to link accounts. The code expires in 10 minutes.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray
        )
        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            digits.forEachIndexed { index, value ->
                OutlinedTextField(
                    value = value,
                    onValueChange = { input ->
                        val digit = input.filter { it.isDigit() }.takeLast(1)
                        digits[index] = digit
                        if (digit.isNotEmpty() && index < 5) {
                            focusRequesters[index + 1].requestFocus()
                        } else if (digit.isEmpty() && index > 0) {
                            focusRequesters[index - 1].requestFocus()
                        } else if (digit.isNotEmpty() && index == 5) {
                            focusManager.clearFocus()
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequesters[index]),
                    singleLine = true,
                    label = { Text("") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center)
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { vm.linkWithOtp(digits.joinToString("")) },
            enabled = !st.isLoading && digits.all { it.isNotEmpty() },
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
                    for (i in digits.indices) digits[i] = ""
                }) {
                    Text("OK")
                }
            }
        )
    }

    if (showErrorDialog && st.linkError != null) {
        AlertDialog(
            onDismissRequest = {
                showErrorDialog = false
                vm.consumeLinkError()
            },
            title = { Text("Error") },
            text = { Text(st.linkError) },
            confirmButton = {
                TextButton(onClick = {
                    showErrorDialog = false
                    vm.consumeLinkError()
                }) { Text("OK") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorRulesScreen(vm: AppViewModel) {
    val st = vm.state
    val displayRules = remember { RuleType.values().filterNot { it == RuleType.INACTIVITY } }
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
        selectedUser?.let { vm.observeRulesForProtected(it.uid) } ?: vm.clearSelectedProtectedRules()
    }

    LazyColumn(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
        item {
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
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
                    fun ruleLabel(type: RuleType): String {
                        val base = type.displayName()
                        val rule = bundle.requested.firstOrNull { it.type == type }
                        return when (type) {
                            RuleType.PROLONGED_INACTIVITY -> {
                                val min = rule?.params?.inactivityDurationMin
                                if (min != null) "$base ($min min)" else base
                            }
                            RuleType.GEOFENCE -> {
                                val areas = rule?.params?.geofenceAreas
                                if (areas.isNullOrEmpty()) base else {
                                    if (areas.size == 1) {
                                        val a = areas.first()
                                        val lat = String.format(Locale.getDefault(), "%.5f", a.latitude)
                                        val lon = String.format(Locale.getDefault(), "%.5f", a.longitude)
                                        "$base ($lat, $lon, ${a.radiusMeters.toInt()} m)"
                                    } else {
                                        "$base (${areas.size} areas)"
                                    }
                                }
                            }
                            else -> base
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Current Authorizations", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("Green = Authorized, Red = Denied", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Spacer(Modifier.height(8.dp))

                            displayRules.forEach { type ->
                                val isAuth = bundle.authorizedTypes.contains(type)
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = ruleLabel(type),
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

    if (showRequestDialog) {
        selectedUser?.let { user ->
            RequestRulesDialog(
                user = user,
                onDismiss = { showRequestDialog = false },
                onSend = { types, params ->
                    vm.requestRulesForProtected(user.uid, types, params)
                    showRequestDialog = false
                }
            )
        }
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
    var prolongedInactivity by remember { mutableStateOf(false) }
    var panic by remember { mutableStateOf(false) }

    var maxSpeed by remember { mutableStateOf("") }
    var inactMin by remember { mutableStateOf("") }
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
                RuleToggle("Prolonged Inactivity", prolongedInactivity) { prolongedInactivity = it }
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
                if (prolongedInactivity) {
                    OutlinedTextField(
                        value = inactMin,
                        onValueChange = { inactMin = it },
                        label = { Text("Inactivity minutes") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                if (geofence) {
                    Text("Geofence Radius", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
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
                    if (prolongedInactivity) add(RuleType.PROLONGED_INACTIVITY)
                    if (panic) add(RuleType.PANIC)
                }
                val params = RuleParams(
                    maxSpeed = maxSpeed.toFloatOrNull(),
                    inactivityDurationMin = inactMin.toIntOrNull(),
                    geofenceRadiusMeters = if (geofence) geoRadius.toDoubleOrNull() else null
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
        verticalAlignment = Alignment.CenterVertically) {
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
