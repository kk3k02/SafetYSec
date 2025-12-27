package pt.a2025121082.isec.safetysec.ui.protected

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import pt.a2025121082.isec.safetysec.data.model.Alert
import pt.a2025121082.isec.safetysec.data.model.RuleType
import pt.a2025121082.isec.safetysec.data.model.TimeWindow
import pt.a2025121082.isec.safetysec.data.model.User
import pt.a2025121082.isec.safetysec.viewmodel.AppViewModel
import pt.a2025121082.isec.safetysec.viewmodel.AuthViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * Protected history screen.
 */
@Composable
fun ProtectedHistoryScreen(vm: AppViewModel) {
    val st = vm.state
    val sdf = remember { SimpleDateFormat("HH:mm:ss dd/MM", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        if (st.myAlerts.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History, 
                        contentDescription = null, 
                        modifier = Modifier.size(64.dp), 
                        tint = Color.LightGray
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("No recent alerts.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item { Spacer(Modifier.height(8.dp)) }
                items(st.myAlerts) { alert ->
                    AlertHistoryItem(alert, sdf)
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
fun AlertHistoryItem(alert: Alert, sdf: SimpleDateFormat) {
    val isCancelled = alert.status == "CANCELLED"
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCancelled) Color(0xFFF8F9FA) else Color(0xFFFFF5F5)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isCancelled) Color.LightGray.copy(alpha = 0.2f) else Color.Red.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning, 
                        contentDescription = null, 
                        tint = if (isCancelled) Color.Gray else Color.Red,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(Modifier.width(12.dp))
                
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "${alert.type.displayName()} ALERT",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isCancelled) Color.DarkGray else Color.Red,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isCancelled) "Cancelled by User" else "Sent to Monitor",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCancelled) Color.Gray else Color(0xFFD32F2F)
                    )
                }
                
                Text(
                    text = sdf.format(Date(alert.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
            
            if (alert.location != null) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Place, 
                        contentDescription = null, 
                        modifier = Modifier.size(14.dp), 
                        tint = Color.Gray
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "GPS: ${String.format("%.5f", alert.location.latitude)}, ${String.format("%.5f", alert.location.longitude)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
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
    var showAddDialog by remember { mutableStateOf(false) }
    var showRemovalSuccessDialog by remember { mutableStateOf(false) }
    var showAdditionSuccessDialog by remember { mutableStateOf(false) }

    LaunchedEffect(st.isRemovalSuccessful) {
        if (st.isRemovalSuccessful) showRemovalSuccessDialog = true
    }

    LaunchedEffect(st.isAdditionSuccessful) {
        if (st.isAdditionSuccessful) showAdditionSuccessDialog = true
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Window")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            if (st.timeWindows.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No time windows defined.", color = Color.Gray)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item { Spacer(Modifier.height(8.dp)) }
                    items(st.timeWindows) { window ->
                        TimeWindowCard(window, onRemove = { vm.removeTimeWindow(window.id) })
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    if (showAddDialog) {
        AddTimeWindowDialog(
            onDismiss = { showAddDialog = false },
            onSave = { days, start, end ->
                vm.addTimeWindow(days, start, end)
                showAddDialog = false
            }
        )
    }

    if (showAdditionSuccessDialog) {
        AlertDialog(
            onDismissRequest = { 
                showAdditionSuccessDialog = false
                vm.consumeAdditionSuccess()
            },
            title = { Text("Window Added") },
            text = { Text("The protection time window has been successfully saved.") },
            confirmButton = {
                TextButton(onClick = { 
                    showAdditionSuccessDialog = false
                    vm.consumeAdditionSuccess()
                }) { Text("OK") }
            }
        )
    }

    if (showRemovalSuccessDialog) {
        AlertDialog(
            onDismissRequest = { 
                showRemovalSuccessDialog = false
                vm.consumeRemovalSuccess()
            },
            title = { Text("Window Removed") },
            text = { Text("The protection time window has been successfully deleted.") },
            confirmButton = {
                TextButton(onClick = { 
                    showRemovalSuccessDialog = false
                    vm.consumeRemovalSuccess()
                }) { Text("OK") }
            }
        )
    }
}

@Composable
fun TimeWindowCard(window: TimeWindow, onRemove: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(window.daysToString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Active from ${window.startHour}:00 to ${window.endHour}:00",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTimeWindowDialog(onDismiss: () -> Unit, onSave: (List<Int>, Int, Int) -> Unit) {
    var selectedDays by remember { mutableStateOf(setOf<Int>()) }
    var timeRange by remember { mutableStateOf(8f..17f) }
    
    val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Protection Window") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text("Select Days:", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(8.dp))
                
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (1..4).forEach { day ->
                            FilterChip(
                                selected = selectedDays.contains(day),
                                onClick = { 
                                    selectedDays = if (selectedDays.contains(day)) selectedDays - day else selectedDays + day
                                },
                                label = { Text(dayNames[day-1]) }
                            )
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        (5..7).forEach { day ->
                            FilterChip(
                                selected = selectedDays.contains(day),
                                onClick = { 
                                    selectedDays = if (selectedDays.contains(day)) selectedDays - day else selectedDays + day
                                },
                                label = { Text(dayNames[day-1]) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Text(
                    "Active Hours: ${timeRange.start.toInt()}:00 - ${timeRange.endInclusive.toInt()}:00",
                    style = MaterialTheme.typography.labelLarge
                )
                RangeSlider(
                    value = timeRange,
                    onValueChange = { timeRange = it },
                    valueRange = 0f..23f,
                    steps = 22
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(selectedDays.toList(), timeRange.start.toInt(), timeRange.endInclusive.toInt()) },
                enabled = selectedDays.isNotEmpty() && timeRange.start < timeRange.endInclusive
            ) { Text("Save Window") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Screen for managing linked Monitors and authorizing monitoring permissions.
 */
@Composable
fun ProtectedMonitorsAndRulesScreen(vm: AppViewModel) {
    val st = vm.state
    var showOtpDialog by remember { mutableStateOf(false) }
    var showRemovalSuccessDialog by remember { mutableStateOf(false) }
    var showUpdateSuccessDialog by remember { mutableStateOf(false) }
    var pendingRequestMonitor by remember { mutableStateOf<Pair<User, List<RuleType>>?>(null) }

    LaunchedEffect(st.isRemovalSuccessful) {
        if (st.isRemovalSuccessful) showRemovalSuccessDialog = true
    }

    LaunchedEffect(st.monitorRuleBundles, st.myLinkedMonitors) {
        st.myLinkedMonitors.forEach { monitor ->
            val bundle = st.monitorRuleBundles.find { it.monitorId == monitor.uid }
            if (bundle != null && bundle.requested.isNotEmpty()) {
                val requestedTypes = bundle.requested.map { it.type }
                val notYetAuthorized = requestedTypes.filter { !bundle.authorizedTypes.contains(it) }
                if (notYetAuthorized.isNotEmpty()) pendingRequestMonitor = monitor to requestedTypes
            }
        }
    }

    LaunchedEffect(st.myOtp) {
        if (st.myOtp != null) showOtpDialog = true
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        item {
            Button(
                onClick = { vm.generateOtp() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !st.isLoading
            ) { Text("Generate OTP (share with Monitor)") }
        }

        item {
            Text("Your Monitors", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        if (st.myLinkedMonitors.isEmpty()) {
            item { Text("No monitors linked.", style = MaterialTheme.typography.bodyMedium, color = Color.Gray) }
        } else {
            items(st.myLinkedMonitors) { monitor ->
                val bundle = st.monitorRuleBundles.find { it.monitorId == monitor.uid }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    val authorized = remember(monitor.uid, bundle?.authorizedTypes) {
                        mutableStateListOf<RuleType>().apply { addAll(bundle?.authorizedTypes ?: emptyList()) }
                    }
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(40.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(monitor.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(monitor.email, style = MaterialTheme.typography.bodySmall)
                            }
                            IconButton(onClick = { vm.removeMonitor(monitor.uid) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        Divider(Modifier.padding(vertical = 12.dp))
                        Text("Grant Permissions:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        
                        RuleType.values().forEach { type ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = type.displayName(), style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = authorized.contains(type),
                                    onCheckedChange = { on -> if (on) authorized.add(type) else authorized.remove(type) }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = { 
                                vm.saveAuthorizations(monitor.uid, authorized.toList(), null)
                                showUpdateSuccessDialog = true 
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Save Permissions") }
                    }
                }
            }
        }
        item {
            st.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }

    if (showUpdateSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showUpdateSuccessDialog = false },
            title = { Text("Permissions Updated") },
            text = { Text("Your monitoring permissions have been successfully updated.") },
            confirmButton = {
                TextButton(onClick = { showUpdateSuccessDialog = false }) { Text("OK") }
            }
        )
    }

    if (showOtpDialog && st.myOtp != null) {
        AlertDialog(
            onDismissRequest = { showOtpDialog = false },
            title = { Text("Association Code") },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Share this 6-digit code with your Monitor.")
                    Spacer(Modifier.height(16.dp))
                    Text(text = st.myOtp!!, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text("Code expires in 10 minutes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            },
            confirmButton = { TextButton(onClick = { showOtpDialog = false }) { Text("Close") } }
        )
    }

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
                    requestedTypes.forEach { type -> Text("• ${type.displayName()}", fontWeight = FontWeight.SemiBold) }
                    Spacer(Modifier.height(12.dp))
                    Text("Do you want to grant these permissions?", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = { 
                    vm.saveAuthorizations(monitor.uid, requestedTypes, null)
                    pendingRequestMonitor = null 
                }) { Text("Accept All") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRequestMonitor = null }) { Text("Decide Later") }
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
    val context = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var inactivityMin by remember(st.inactivityDurationMin) { mutableStateOf(st.inactivityDurationMin.toString()) }
    var showSecurityPopup by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { authVm.loadAccountInfo() }
    
    LaunchedEffect(st.isSecurityUpdateSuccessful) {
        if (st.isSecurityUpdateSuccessful) {
            showSecurityPopup = true
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
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
            modifier = Modifier.fillMaxWidth(), 
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = { vm.updateCancelPin(pin) }, modifier = Modifier.fillMaxWidth()) { Text("Update PIN") }
        
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = inactivityMin,
            onValueChange = { inactivityMin = it },
            label = { Text("Inactivity alert minutes") },
            placeholder = { Text("e.g. 15") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { vm.updateInactivityDuration(inactivityMin) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
        ) {
            Text("Update Inactivity Settings")
        }

        Spacer(Modifier.height(24.dp))
        Button(onClick = onSwitchToMonitor, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) { Text("Switch to Monitor Mode") }
        
        authSt.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        authSt.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
        st.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
    }

    if (showSecurityPopup) {
        AlertDialog(
            onDismissRequest = { 
                showSecurityPopup = false
                vm.consumeSecurityUpdateSuccess()
            },
            title = { Text("Settings Saved") },
            text = { Text("Your security settings (PIN and inactivity duration) have been successfully updated.") },
            confirmButton = {
                TextButton(onClick = { 
                    showSecurityPopup = false
                    vm.consumeSecurityUpdateSuccess()
                }) {
                    Text("OK")
                }
            }
        )
    }
}

@Composable
fun ProtectedCancelAlertDialog(vm: AppViewModel) {
    val st = vm.state
    val context = LocalContext.current

    LaunchedEffect(st.isCancelWindowOpen) {
        if (st.isCancelWindowOpen) {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }

            while (st.isCancelWindowOpen) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(500)
                }
                delay(1000)
            }
        }
    }

    if (st.isCancelWindowOpen) {
        var typed by remember { mutableStateOf("") }
        fun formatTime(s: Int) = "00:${s.toString().padStart(2, '0')}"

        AlertDialog(
            onDismissRequest = { },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red)
                    Spacer(Modifier.width(8.dp))
                    Text("Alert Triggered!", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("An emergency alert is about to be sent.")
                    Spacer(Modifier.height(16.dp))
                    Text(text = formatTime(st.cancelSecondsLeft), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.ExtraBold, color = if (st.cancelSecondsLeft <= 3) Color.Red else MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Text("Enter PIN to cancel:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    
                    // PIN input with error state
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it },
                        label = { Text("Enter PIN") },
                        isError = st.cancelPinError != null,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    
                    // Display PIN error message if present
                    st.cancelPinError?.let { errorMsg ->
                        Text(
                            text = errorMsg,
                            color = Color.Red,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { vm.tryCancelAlert(typed) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Cancel Alert")
                }
            }
        )
    }
}
