package pt.a2025121082.isec.safetysec.ui.protected

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import pt.a2025121082.isec.safetysec.data.model.Alert
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.launch
import pt.a2025121082.isec.safetysec.data.model.GeofenceArea
import pt.a2025121082.isec.safetysec.data.model.MonitoringRule
import pt.a2025121082.isec.safetysec.data.model.RuleType
import pt.a2025121082.isec.safetysec.data.model.TimeWindow
import pt.a2025121082.isec.safetysec.data.model.User
import pt.a2025121082.isec.safetysec.data.repository.MonitorRulesBundle
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
    var selectedAlertForVideo by remember { mutableStateOf<Alert?>(null) }

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
                    AlertHistoryItem(
                        alert = alert,
                        sdf = sdf,
                        onClick = { if (!alert.videoUrl.isNullOrBlank()) selectedAlertForVideo = alert }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    selectedAlertForVideo?.let { alert ->
        alert.videoUrl?.let { videoUrl ->
            VideoPlaybackDialog(
                videoUrl = videoUrl,
                onDismiss = { selectedAlertForVideo = null }
            )
        }
    }
}

@Composable
fun AlertHistoryItem(alert: Alert, sdf: SimpleDateFormat, onClick: () -> Unit) {
    val isCancelled = alert.status == "CANCELLED"
    val hasVideo = !alert.videoUrl.isNullOrBlank()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = hasVideo, onClick = onClick),
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

                if (hasVideo) {
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = "Has Video",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(end = 8.dp)
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

@Composable
fun VideoPlaybackDialog(videoUrl: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Evidence Video", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = true
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close Player")
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
    var otpToShow by remember { mutableStateOf<String?>(null) }
    var showRemovalSuccessDialog by remember { mutableStateOf(false) }
    var showUpdateSuccessDialog by remember { mutableStateOf(false) }
    var pendingRequestMonitor by remember { mutableStateOf<Pair<User, List<MonitoringRule>>?>(null) }
    var shownRequestKeys by remember { mutableStateOf(setOf<String>()) }
    val grantableRules = remember { RuleType.values().filterNot { it == RuleType.INACTIVITY } }
    var geofenceBaseLocation by remember { mutableStateOf<GeoPoint?>(null) }
    val scope = rememberCoroutineScope()

    fun ruleLabel(type: RuleType, bundle: MonitorRulesBundle?): String {
        val base = type.displayName()
        if (bundle == null) return base
        val rule = bundle.requested.firstOrNull { it.type == type }
        return when (type) {
            RuleType.SPEED -> {
                val max = rule?.params?.maxSpeed
                if (max != null) "$base (${max.toInt()} km/h)" else base
            }
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

    LaunchedEffect(st.isRemovalSuccessful) {
        if (st.isRemovalSuccessful) showRemovalSuccessDialog = true
    }

    LaunchedEffect(st.monitorRuleBundles, st.myLinkedMonitors, shownRequestKeys) {
        st.myLinkedMonitors.forEach { monitor ->
            val bundle = st.monitorRuleBundles.find { it.monitorId == monitor.uid }
            if (bundle != null && bundle.requested.isNotEmpty()) {
                val requestedRules = bundle.requested
                val requestedTypes = requestedRules.map { it.type }
                val notYetAuthorized = requestedTypes.filter { !bundle.authorizedTypes.contains(it) }
                if (notYetAuthorized.isNotEmpty()) {
                    val requestKey = monitor.uid + ":" + requestedTypes.sorted().joinToString(",")
                    if (!shownRequestKeys.contains(requestKey)) {
                        pendingRequestMonitor = monitor to requestedRules
                    }
                }
            }
        }
    }

    LaunchedEffect(st.myOtp) {
        if (st.myOtp != null) {
            otpToShow = st.myOtp
            showOtpDialog = true
        }
    }

    LaunchedEffect(pendingRequestMonitor) {
        val requiresGeofence = pendingRequestMonitor?.second?.any { it.type == RuleType.GEOFENCE } == true
        geofenceBaseLocation = if (requiresGeofence) {
            vm.getCurrentLocation()
        } else {
            null
        }
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
                        HorizontalDivider(Modifier.padding(vertical = 12.dp))
                        Text("Grant Permissions:", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)

                        grantableRules.forEach { type ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = ruleLabel(type, bundle), style = MaterialTheme.typography.bodyMedium)
                                Switch(
                                    checked = authorized.contains(type),
                                    onCheckedChange = { on -> if (on) authorized.add(type) else authorized.remove(type) }
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Button(
                            onClick = {
                                vm.saveAuthorizations(monitor.uid, authorized.toList(), null, null)
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

    if (showOtpDialog && otpToShow != null) {
        AlertDialog(
            onDismissRequest = {
                showOtpDialog = false
                otpToShow = null
            },
            title = { Text("Association Code") },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Share this 6-digit code with your Monitor.")
                    Spacer(Modifier.height(16.dp))
                    val otp = otpToShow ?: ""
                    Text(
                        text = otp,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Code expires in 10 minutes.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showOtpDialog = false
                    otpToShow = null
                }) { Text("Close") }
            }
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

    pendingRequestMonitor?.let { (monitor, requestedRules) ->
        val requestedTypes = requestedRules.map { it.type }
        val requestKey = remember(monitor.uid, requestedTypes) {
            monitor.uid + ":" + requestedTypes.sorted().joinToString(",")
        }
        val geofenceRadius = requestedRules.firstOrNull { it.type == RuleType.GEOFENCE }
            ?.params?.geofenceRadiusMeters
        val canAccept = !requestedTypes.contains(RuleType.GEOFENCE) || geofenceBaseLocation != null
        AlertDialog(
            onDismissRequest = {
                shownRequestKeys = shownRequestKeys + requestKey
                pendingRequestMonitor = null
            },
            title = { Text("New Access Request") },
            text = {
                Column {
                    Text("${monitor.name} is requesting access to the following rules:")
                    Spacer(Modifier.height(8.dp))
                    requestedTypes.forEach { type ->
                        val minutes = if (type == RuleType.PROLONGED_INACTIVITY) {
                            requestedRules.firstOrNull { it.type == type }?.params?.inactivityDurationMin
                        } else null
                        val maxSpeed = if (type == RuleType.SPEED) {
                            requestedRules.firstOrNull { it.type == type }?.params?.maxSpeed
                        } else null
                        val label = when {
                            type == RuleType.GEOFENCE && geofenceRadius != null && geofenceBaseLocation != null -> {
                                val lat = String.format(Locale.getDefault(), "%.5f", geofenceBaseLocation!!.latitude)
                                val lon = String.format(Locale.getDefault(), "%.5f", geofenceBaseLocation!!.longitude)
                                "${type.displayName()} (${geofenceRadius.toInt()} m) @ $lat, $lon"
                            }
                            type == RuleType.GEOFENCE && geofenceRadius != null -> {
                                "${type.displayName()} (${geofenceRadius.toInt()} m) @ location unavailable"
                            }
                            maxSpeed != null -> "${type.displayName()} (${maxSpeed.toInt()} km/h)"
                            minutes != null -> "${type.displayName()} ($minutes min)"
                            else -> type.displayName()
                        }
                        Text("- $label", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Do you want to grant these permissions?", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        if (requestedTypes.contains(RuleType.GEOFENCE)) {
                            geofenceBaseLocation = vm.getCurrentLocation()
                        }
                        val inactivityMin = requestedRules.firstOrNull { it.type == RuleType.PROLONGED_INACTIVITY }
                            ?.params?.inactivityDurationMin
                        val geofenceAreas = if (requestedTypes.contains(RuleType.GEOFENCE)) {
                            val loc = geofenceBaseLocation
                            val radius = geofenceRadius
                            if (loc != null && radius != null) {
                                listOf(GeofenceArea(loc.latitude, loc.longitude, radius))
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                        if (requestedTypes.contains(RuleType.GEOFENCE) && geofenceAreas == null) return@launch
                        vm.saveAuthorizations(monitor.uid, requestedTypes, inactivityMin, geofenceAreas)
                        shownRequestKeys = shownRequestKeys + requestKey
                        pendingRequestMonitor = null
                    }
                }, enabled = canAccept) { Text("Accept All") }
            },
            dismissButton = {
                TextButton(onClick = {
                    shownRequestKeys = shownRequestKeys + requestKey
                    pendingRequestMonitor = null
                }) { Text("Decline") }
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
    var inactivityMin by remember(st.inactivityDurationMin) { mutableStateOf(st.inactivityDurationMin.toString()) }
    var showSecurityPopup by remember { mutableStateOf(false) }
    var securityPopupMessage by remember { mutableStateOf("Your security settings (PIN and inactivity duration) have been successfully updated.") }
    var showPinDialog by remember { mutableStateOf(false) }
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }

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

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Fall detection", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Run background monitoring on this device",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Switch(
                checked = st.isFallDetectionEnabled,
                onCheckedChange = { vm.toggleFallDetection(context) }
            )
        }

        Button(
            onClick = {
                oldPin = ""
                newPin = ""
                pinError = null
                showPinDialog = true
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Change PIN") }

        Spacer(Modifier.height(24.dp))
        Button(onClick = onSwitchToMonitor, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)) { Text("Switch to Monitor Mode") }

        authSt.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        authSt.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
        st.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Change PIN") },
            text = {
                Column {
                    OutlinedTextField(
                        value = oldPin,
                        onValueChange = { input ->
                            val digitsOnly = input.filter { it.isDigit() }
                            oldPin = digitsOnly.take(4)
                            pinError = null
                        },
                        label = { Text("Current PIN") },
                        placeholder = { Text("4 digits") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = pinError != null
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { input ->
                            val digitsOnly = input.filter { it.isDigit() }
                            newPin = digitsOnly.take(4)
                            pinError = when {
                                newPin.length == 4 && newPin == st.me?.alertCancelCode.orEmpty() ->
                                    "Please enter a different PIN."
                                newPin.length == 4 && oldPin.length == 4 && oldPin != st.me?.alertCancelCode.orEmpty() ->
                                    "Current PIN is incorrect."
                                else -> null
                            }
                        },
                        label = { Text("New PIN") },
                        placeholder = { Text("4 digits") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                val currentPin = st.me?.alertCancelCode.orEmpty()
                                when {
                                    oldPin.length < 4 -> pinError = "Current PIN must be 4 digits."
                                    oldPin != currentPin -> pinError = "Current PIN is incorrect."
                                    newPin.length < 4 -> pinError = "New PIN must be 4 digits."
                                    newPin == currentPin -> pinError = "Please enter a different PIN."
                                    else -> {
                                        securityPopupMessage = "Your PIN has been updated successfully."
                                        vm.updateCancelPin(newPin)
                                        showPinDialog = false
                                    }
                                }
                            }
                        ),
                        isError = pinError != null
                    )
                    pinError?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                val currentPin = st.me?.alertCancelCode.orEmpty()
                val canSave = oldPin.length == 4 && newPin.length == 4
                TextButton(onClick = {
                    when {
                        oldPin.length < 4 -> pinError = "Current PIN must be 4 digits."
                        oldPin != currentPin -> pinError = "Current PIN is incorrect."
                        newPin.length < 4 -> pinError = "New PIN must be 4 digits."
                        newPin == currentPin -> pinError = "Please enter a different PIN."
                        else -> {
                            securityPopupMessage = "Your PIN has been updated successfully."
                            vm.updateCancelPin(newPin)
                            showPinDialog = false
                        }
                    }
                }, enabled = canSave) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showSecurityPopup) {
        AlertDialog(
            onDismissRequest = { 
                showSecurityPopup = false
                vm.consumeSecurityUpdateSuccess()
            },
            title = { Text("Settings Saved") },
            text = { Text(securityPopupMessage) },
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
                    val type = st.cancelAlertType?.displayName()?.uppercase(Locale.getDefault()) ?: "ALERT"
                    Text("$type TRIGGERED!", color = Color.Red, fontWeight = FontWeight.Bold)
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

