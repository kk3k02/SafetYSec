package pt.a2025121082.isec.safetysec

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import dagger.hilt.android.AndroidEntryPoint
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

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private var appViewModel: AppViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        appViewModel?.resetInactivityTimer()
        return super.dispatchTouchEvent(ev)
    }
}

private object Routes {
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val RESET_PASSWORD = "reset_password"
    const val PROFILE = "profile"
    const val ROLE_PICKER = "role_picker"
    const val PROTECTED_FLOW = "protected_flow"
    const val MONITOR_FLOW = "monitor_flow"
}

@Composable
private fun SafetYSecApp(
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = hiltViewModel(),
    appViewModel: AppViewModel
) {
    val authState = authViewModel.uiState
    val appState = appViewModel.state
    val context = LocalContext.current

    val permissionsToRequest = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    var permissionsGranted by remember { 
        mutableStateOf(permissionsToRequest.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) 
    }
    
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        permissionsGranted = results.values.all { it }
    }

    LaunchedEffect(Unit) {
        if (!permissionsGranted) launcher.launch(permissionsToRequest)
        authViewModel.refreshAuthState()
    }

    LaunchedEffect(authState.isAuthenticated, appState.me?.uid) {
        if (authState.isAuthenticated) {
            val me = appState.me
            if (me == null) {
                appViewModel.loadMyProfile()
            } else {
                if (me.roles.contains("Monitor")) {
                    appViewModel.startMonitoringDashboard(me.uid, context)
                }
            }
        } else {
            appViewModel.clear()
        }
    }

    LaunchedEffect(authState.isAuthenticated, appState.me?.uid) {
        if (!authState.isAuthenticated) return@LaunchedEffect
        val me = appState.me ?: return@LaunchedEffect
        val currentEntry = navController.currentBackStackEntry
        val currentRoute = currentEntry?.destination?.route
        
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

        appState.pendingAlerts.firstOrNull()?.let { alert ->
            MonitorGlobalAlertPopup(
                alert = alert,
                onDismiss = { appViewModel.dismissIncomingAlert() }
            )
        }
        
        appState.showInactivityAlertPopup?.let { minutes ->
            AlertDialog(
                onDismissRequest = { appViewModel.dismissInactivityPopup() },
                title = { Text("Inactivity Alert Sent") },
                text = { Text("Your monitor has been notified that you have been inactive for $minutes minutes.") },
                confirmButton = {
                    TextButton(onClick = { appViewModel.dismissInactivityPopup() }) {
                        Text("OK")
                    }
                }
            )
        }

        if (appState.isRecordingPopupOpen) {
            EmergencyRecordingPopup(
                appViewModel = appViewModel,
                secondsLeft = appState.recordingSecondsLeft,
                onDismiss = { appViewModel.dismissRecordingPopup() }
            )
        }
    }
}

@Composable
fun EmergencyRecordingPopup(appViewModel: AppViewModel, secondsLeft: Int, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val previewView = remember { PreviewView(context) }
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

    // BINDUJEMY TYLKO TUTAJ - RAZ - Preview i VideoCapture razem
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
                // BINDUJEMY oba use-case'y w jednym kroku
                cameraProvider.bindToLifecycle(
                    ProcessLifecycleOwner.get(), 
                    cameraSelector, 
                    preview, 
                    appViewModel.videoCapture
                )
                // Gdy hardware jest zbindowany, odpalamy zapis w ViewModelu
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.size(12.dp).background(Color.Red.copy(alpha = recAlpha), CircleShape))
                        Spacer(Modifier.width(8.dp))
                        Text("ALERT SENT & RECORDING", style = MaterialTheme.typography.titleMedium, color = Color.Red, fontWeight = FontWeight.Black)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
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
                    val minutes = secondsLeft / 60
                    val seconds = secondsLeft % 60
                    Text(text = String.format("%02d:%02d", minutes, seconds), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black, color = if (secondsLeft <= 5) Color.Red else MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(24.dp))
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
                if (!alert.videoUrl.isNullOrBlank()) {
                    Text("Evidence Video:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(250.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
                        VideoPlayer(videoUrl = alert.videoUrl)
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f))) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Type: ${alert.type.displayName()}", fontWeight = FontWeight.Bold)
                        Text("Time: ${sdf.format(Date(alert.timestamp))}")
                        if (alert.location != null) {
                            Spacer(Modifier.height(8.dp))
                            Text("${String.format("%.5f", alert.location.latitude)}, ${String.format("%.5f", alert.location.longitude)}")
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Confirm & Dismiss", fontWeight = FontWeight.Bold) } }
    )
}

@Composable
fun VideoPlayer(videoUrl: String) {
    val context = LocalContext.current
    val exoPlayer = remember(videoUrl) { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(videoUrl)); prepare(); playWhenReady = false } }
    DisposableEffect(videoUrl) { onDispose { exoPlayer.release() } }
    AndroidView(factory = { ctx -> PlayerView(ctx).apply { player = exoPlayer; useController = true; setBackgroundColor(android.graphics.Color.BLACK) } }, modifier = Modifier.fillMaxSize())
}
