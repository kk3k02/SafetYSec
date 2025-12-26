package pt.a2025121082.isec.safetysec

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SafetYSecTheme { Surface(color = MaterialTheme.colorScheme.background) { SafetYSecApp() } } }
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
    appViewModel: AppViewModel = hiltViewModel()
) {
    val authState = authViewModel.uiState
    val appState = appViewModel.state
    val context = LocalContext.current

    LaunchedEffect(Unit) { authViewModel.refreshAuthState() }

    LaunchedEffect(authState.isAuthenticated, appState.me) {
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

    LaunchedEffect(authState.isAuthenticated, appState.me) {
        if (!authState.isAuthenticated) return@LaunchedEffect
        val me = appState.me ?: return@LaunchedEffect
        val current = navController.currentDestination?.route
        if (current in setOf(Routes.PROTECTED_FLOW, Routes.MONITOR_FLOW, Routes.PROFILE)) return@LaunchedEffect

        when {
            me.roles.contains("Protected") && !me.roles.contains("Monitor") ->
                navController.navigate(Routes.PROTECTED_FLOW) { popUpTo(0) }
            me.roles.contains("Monitor") && !me.roles.contains("Protected") ->
                navController.navigate(Routes.MONITOR_FLOW) { popUpTo(0) }
            else ->
                navController.navigate(Routes.ROLE_PICKER) { popUpTo(0) }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = if (authState.isAuthenticated) Routes.ROLE_PICKER else Routes.LOGIN,
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
                    onGoProtected = { navController.navigate(Routes.PROTECTED_FLOW) { popUpTo(0) } },
                    onGoMonitor = { navController.navigate(Routes.MONITOR_FLOW) { popUpTo(0) } }
                )
            }
            composable(Routes.PROTECTED_FLOW) {
                ProtectedFlow(
                    appViewModel = appViewModel,
                    onSwitchToMonitor = { navController.navigate(Routes.MONITOR_FLOW) { popUpTo(0) } },
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
                    onSwitchToProtected = { navController.navigate(Routes.PROTECTED_FLOW) { popUpTo(0) } },
                    onLogout = {
                        authViewModel.logout()
                        navController.navigate(Routes.LOGIN) { popUpTo(0) }
                    },
                    onProfile = { navController.navigate(Routes.PROFILE) }
                )
            }
        }

        // --- GLOBAL ALERT POPUP (QUEUE) ---
        appState.pendingAlerts.firstOrNull()?.let { alert ->
            MonitorGlobalAlertPopup(
                alert = alert,
                onDismiss = { appViewModel.dismissIncomingAlert() }
            )
        }
    }
}

@Composable
fun MonitorGlobalAlertPopup(alert: Alert, onDismiss: () -> Unit) {
    val sdf = remember { SimpleDateFormat("HH:mm:ss dd/MM/yyyy", Locale.getDefault()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Error, contentDescription = null, tint = Color.Red, modifier = Modifier.size(64.dp)) },
        title = { 
            Text(
                "EMERGENCY ALERT!", 
                color = Color.Red, 
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            ) 
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "User: ${alert.protectedName}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("An alert has been triggered by your protected user.", textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.1f))
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Type: ${alert.type.displayName()}", fontWeight = FontWeight.Bold)
                        Text("Time: ${sdf.format(Date(alert.timestamp))}")
                        if (alert.location != null) {
                            Spacer(Modifier.height(8.dp))
                            Text("Location (GPS):", style = MaterialTheme.typography.labelSmall)
                            Text("${String.format("%.5f", alert.location.latitude)}, ${String.format("%.5f", alert.location.longitude)}")
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                Text(
                    "Please check on them immediately.",
                    fontWeight = FontWeight.SemiBold,
                    color = Color.Red,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("Confirm & Dismiss", fontWeight = FontWeight.Bold)
            }
        }
    )
}
