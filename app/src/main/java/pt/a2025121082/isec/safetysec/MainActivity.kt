package pt.a2025121082.isec.safetysec

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.IntentSender
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.firebase.firestore.GeoPoint
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.tasks.await
import pt.a2025121082.isec.safetysec.data.model.Alert
import pt.a2025121082.isec.safetysec.ui.auth.LoginScreen
import pt.a2025121082.isec.safetysec.ui.auth.RegistrationScreen
import pt.a2025121082.isec.safetysec.ui.flows.MonitorFlow
import pt.a2025121082.isec.safetysec.ui.flows.ProtectedFlow
import pt.a2025121082.isec.safetysec.ui.screens.PasswordResetScreen
import pt.a2025121082.isec.safetysec.ui.screens.ProfileScreen
import pt.a2025121082.isec.safetysec.ui.screens.RolePickerScreen
import pt.a2025121082.isec.safetysec.ui.theme.SafetYSecTheme
import pt.a2025121082.isec.safetysec.viewmodel.AppViewModel
import pt.a2025121082.isec.safetysec.viewmodel.AuthViewModel
import java.text.SimpleDateFormat
import java.util.*

/**
 * The main entry point of the application.
 * Annotated with @AndroidEntryPoint for Hilt dependency injection.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var appViewModel: AppViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // Enables edge-to-edge display for a modern look
        setContent {
            SafetYSecTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val avm: AppViewModel = hiltViewModel()
                    this.appViewModel = avm
                    SafetYSecApp(appViewModel = avm)
                }
            }
        }
    }

    /**
     * Intercepts touch events to reset the inactivity timer in the ViewModel.
     */
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        appViewModel?.resetInactivityTimer()
        return super.dispatchTouchEvent(ev)
    }
}

/**
 * Route constants used for navigation within the app.
 */
private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val RESET_PASSWORD = "reset_password"
    const val PROFILE = "profile"
    const val ROLE_PICKER = "role_picker"
    const val PROTECTED_FLOW = "protected_flow"
    const val MONITOR_FLOW = "monitor_flow"
}

/**
 * Main application composable that handles navigation and core services like location tracking.
 */
@Composable
private fun SafetYSecApp(
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = hiltViewModel(),
    appViewModel: AppViewModel
) {
    val authState = authViewModel.uiState
    val appState = appViewModel.state
    val context = LocalContext.current
    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    
    // State holders for location and speed data
    val lastSpeedKmh = remember { mutableStateOf<Float?>(null) }
    val lastGeoPoint = remember { mutableStateOf<GeoPoint?>(null) }
    val lastLocation = remember { mutableStateOf<Location?>(null) }

    // Permissions required by the app
    val permissionsToRequest = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )
    
    // Check if permissions are already granted
    var permissionsGranted by remember {
        mutableStateOf(permissionsToRequest.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED })
    }

    // Launcher for requesting multiple permissions
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        permissionsGranted = results.values.all { it }
    }

    // Launcher for handling GPS settings resolution
    val gpsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            Log.w("MainActivity", "User declined to enable GPS")
        }
    }

    /**
     * Checks if GPS is enabled and prompts the user to enable it if necessary.
     */
    fun checkGpsSettings() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000).build()
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)

        val client = LocationServices.getSettingsClient(context)
        client.checkLocationSettings(builder.build())
            .addOnFailureListener { exception ->
                if (exception is ResolvableApiException) {
                    try {
                        val intentSenderRequest = IntentSenderRequest.Builder(exception.resolution.intentSender).build()
                        gpsLauncher.launch(intentSenderRequest)
                    } catch (e: IntentSender.SendIntentException) {
                        Log.e("MainActivity", "Error launching GPS resolution", e)
                    }
                }
            }
    }

    // Initial setup: request permissions and refresh auth state
    LaunchedEffect(Unit) {
        if (!permissionsGranted) launcher.launch(permissionsToRequest)
        authViewModel.logout() // Optional: logout on start or based on logic
        authViewModel.refreshAuthState()
    }

    // Check GPS settings once permissions are granted
    LaunchedEffect(permissionsGranted) {
        if (permissionsGranted) {
            checkGpsSettings()
        }
    }

    // Handle continuous location updates when permissions are available
    DisposableEffect(permissionsGranted) {
        if (!permissionsGranted) return@DisposableEffect onDispose { }
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000).build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                lastGeoPoint.value = GeoPoint(location.latitude, location.longitude)
                
                // Calculate speed either from sensor or manually from distance/time
                val speedFromSensor = location.speed.takeIf { it >= 0f }?.let { it * 3.6f }
                val prev = lastLocation.value
                val speedFromDistance = if (prev != null) {
                    val deltaNs = location.elapsedRealtimeNanos - prev.elapsedRealtimeNanos
                    if (deltaNs > 0L) {
                        val deltaSec = deltaNs / 1_000_000_000.0
                        val dist = location.distanceTo(prev)
                        if (dist >= 0f) ((dist / deltaSec) * 3.6).toFloat() else null
                    } else null
                } else null
                
                lastSpeedKmh.value = speedFromSensor ?: speedFromDistance
                lastLocation.value = location
                Log.d(
                    "SpeedMonitor",
                    "loc: lat=${location.latitude} lon=${location.longitude} sSensor=$speedFromSensor sDist=$speedFromDistance"
                )
            }
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, callback, Looper.getMainLooper())
        onDispose { fusedLocationClient.removeLocationUpdates(callback) }
    }

    /**
     * Helper function to fetch the current location.
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): GeoPoint? {
        return try {
            if (!permissionsGranted) return null
            val location = fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null).await()
                ?: fusedLocationClient.lastLocation.await()
            location?.let { GeoPoint(it.latitude, it.longitude) } ?: lastGeoPoint.value
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to get location", e)
            lastGeoPoint.value
        }
    }

    // Provide location and speed data to the AppViewModel
    LaunchedEffect(Unit) {
        appViewModel.setLocationProvider { getCurrentLocation() }
        appViewModel.setSpeedProvider { lastSpeedKmh.value }
    }

    // Handle profile loading and dashboard startup based on user role
    LaunchedEffect(authState.isAuthenticated, appState.me?.uid) {
        if (authState.isAuthenticated) {
            val me = appState.me
            if (me == null) {
                appViewModel.loadMyProfile()
            } else {
                if (me.roles.contains("Monitor")) {
                    appViewModel.startMonitoringDashboard(me.uid)
                }
            }
        } else {
            appViewModel.clear()
        }
    }

    // Logic for automatic navigation based on authentication status and user roles
    LaunchedEffect(authState.isAuthenticated, appState.me?.uid) {
        if (!authState.isAuthenticated) return@LaunchedEffect
        val me = appState.me ?: return@LaunchedEffect
        val currentEntry = navController.currentBackStackEntry
        val currentRoute = currentEntry?.destination?.route

        // Don't navigate away if already in a main flow or profile
        if (currentRoute in setOf(Routes.PROTECTED_FLOW, Routes.MONITOR_FLOW, Routes.PROFILE)) return@LaunchedEffect

        val target = when {
            me.roles.contains("Protected") && !me.roles.contains("Monitor") -> Routes.PROTECTED_FLOW
            me.roles.contains("Monitor") && !me.roles.contains("Protected") -> Routes.MONITOR_FLOW
            else -> Routes.ROLE_PICKER
        }

        if (currentRoute != target) {
            navController.navigate(target) {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
            }
        }
    }

    // Main UI structure with NavHost and global overlays (Popups)
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.LOGIN,
            modifier = Modifier.fillMaxSize()
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(
                    viewModel = authViewModel,
                    onNavigateToRegistration = { navController.navigate(Routes.REGISTER) },
                    onNavigateToResetPassword = { navController.navigate(Routes.RESET_PASSWORD) },
                    onLoginSuccess = {}
                )
            }
            composable(Routes.REGISTER) {
                RegistrationScreen(
                    viewModel = authViewModel,
                    onNavigateToLogin = {
                        navController.navigate(Routes.LOGIN) { popUpTo(Routes.REGISTER) { inclusive = true } }
                    }
                )
            }
            composable(Routes.RESET_PASSWORD) {
                PasswordResetScreen(authViewModel = authViewModel, onDone = { navController.popBackStack() })
            }
            composable(Routes.PROFILE) {
                ProfileScreen(onNavigateBack = { navController.popBackStack() }, viewModel = authViewModel)
            }
            composable(Routes.ROLE_PICKER) {
                RolePickerScreen(
                    onGoProtected = { navController.navigate(Routes.PROTECTED_FLOW) },
                    onGoMonitor = { navController.navigate(Routes.MONITOR_FLOW) }
                )
            }
            composable(Routes.PROTECTED_FLOW) {
                ProtectedFlow(
                    appViewModel = appViewModel,
                    onSwitchToMonitor = { navController.navigate(Routes.MONITOR_FLOW) },
                    onLogout = {
                        authViewModel.logout()
                        navController.navigate(Routes.LOGIN) { popUpTo(0) }
                    },
                    onProfile = { navController.navigate(Routes.PROFILE) }
                )
            }
            composable(Routes.MONITOR_FLOW) {
                MonitorFlow(
                    appViewModel = appViewModel,
                    onSwitchToProtected = { navController.navigate(Routes.PROTECTED_FLOW) },
                    onLogout = {
                        authViewModel.logout()
                        navController.navigate(Routes.LOGIN) { popUpTo(0) }
                    },
                    onProfile = { navController.navigate(Routes.PROFILE) }
                )
            }
        }

        // Global popup for incoming alerts (for Monitors)
        appState.pendingAlerts.firstOrNull()?.let { alert ->
            MonitorGlobalAlertPopup(
                alert = alert,
                onDismiss = { appViewModel.dismissIncomingAlert() }
            )
        }

        // Global popup for emergency recording (for Protected users)
        if (appState.isRecordingPopupOpen) {
            EmergencyRecordingPopup(
                appViewModel = appViewModel,
                secondsLeft = appState.recordingSecondsLeft,
                onDismiss = { appViewModel.dismissRecordingPopup() }
            )
        }
    }
}

/**
 * A popup dialog that handles camera preview and video recording during an emergency.
 */
@Composable
fun EmergencyRecordingPopup(appViewModel: AppViewModel, secondsLeft: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }
    
    // Animation for the "REC" dot
    val infiniteTransition = rememberInfiniteTransition(label = "rec")
    val recAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "recAlpha"
    )

    // Bind CameraX preview and video capture use cases
    LaunchedEffect(Unit) {
        val cameraProviderProvider = ProcessCameraProvider.getInstance(context)
        cameraProviderProvider.addListener({
            val cameraProvider = cameraProviderProvider.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    ProcessLifecycleOwner.get(),
                    cameraSelector,
                    preview,
                    appViewModel.videoCapture
                )
                // Start the actual video recording in the ViewModel
                appViewModel.startActualRecording()
            } catch (e: Exception) {
                Log.e("RecordingPopup", "Binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    Dialog(
        onDismissRequest = { },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false, usePlatformDefaultWidth = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)).padding(24.dp), contentAlignment = Alignment.Center) {
            Card(
                modifier = Modifier.fillMaxWidth().wrapContentHeight().border(2.dp, Color.Red.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    // Alert Header with blinking REC dot
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.size(12.dp).background(Color.Red.copy(alpha = recAlpha), CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text("ALERT SENT & RECORDING", style = MaterialTheme.typography.titleMedium, color = Color.Red, fontWeight = FontWeight.Black)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Camera Preview Area
                    Box(modifier = Modifier.fillMaxWidth().height(350.dp).clip(RoundedCornerShape(16.dp)).background(Color.DarkGray)) {
                        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                        Box(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.TopEnd) {
                            Surface(color = Color.Black.copy(alpha = 0.6f), shape = RoundedCornerShape(8.dp)) {
                                Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Videocam, contentDescription = null, tint = Color.Red, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("LIVE", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Countdown timer
                    val minutes = secondsLeft / 60
                    val seconds = secondsLeft % 60
                    Text(text = String.format("%02d:%02d", minutes, seconds), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black, color = if (secondsLeft <= 5) Color.Red else MaterialTheme.colorScheme.onSurface)
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Dismiss button or progress bar
                    if (secondsLeft == 0) {
                        Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp)) {
                            Text("I AM SAFE - CLOSE", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        LinearProgressIndicator(progress = { secondsLeft / 30f }, modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape), color = Color.Red, trackColor = Color.Red.copy(alpha = 0.1f))
                    }
                }
            }
        }
    }
}

/**
 * A dialog displayed to Monitors when an emergency alert is received.
 * Shows user details, alert type, location, and the evidence video.
 */
@Composable
fun MonitorGlobalAlertPopup(alert: Alert, onDismiss: () -> Unit) {
    val sdf = remember { SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Error, contentDescription = null, tint = Color.Red, modifier = Modifier.size(64.dp)) },
        title = { Text("EMERGENCY ALERT!", color = Color.Red, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(text = "User: ${alert.protectedName}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(12.dp))
                
                // Evidence Video Player
                if (!alert.videoUrl.isNullOrBlank()) {
                    Text("Evidence Video:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
                        VideoPlayer(videoUrl = alert.videoUrl)
                    }
                    Spacer(Modifier.height(12.dp))
                }
                
                // Alert Details Card
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f))) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Type: ${alert.type.displayName()}", fontWeight = FontWeight.Bold)
                        Text("Time: ${sdf.format(Date(alert.timestamp))}")
                        if (alert.location != null) {
                            Spacer(Modifier.height(8.dp))
                            Text(String.format("%.5f, %.5f", alert.location.latitude, alert.location.longitude))
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Confirm & Dismiss", fontWeight = FontWeight.Bold) } }
    )
}

/**
 * A simple video player using ExoPlayer to play evidence videos.
 */
@Composable
fun VideoPlayer(videoUrl: String) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUrl) { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(videoUrl)); prepare(); playWhenReady = false } }
    DisposableEffect(videoUrl) { onDispose { exoPlayer.release() } }
    AndroidView(factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer; useController = true; setBackgroundColor(android.graphics.Color.BLACK) } }, modifier = Modifier.fillMaxSize())
}
