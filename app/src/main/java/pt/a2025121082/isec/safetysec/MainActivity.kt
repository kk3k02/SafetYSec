package pt.a2025121082.isec.safetysec

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
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

/**
 * Main entry Activity for the app.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SafetYSecTheme { SafetYSecApp() } }
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

    LaunchedEffect(Unit) { authViewModel.refreshAuthState() }

    LaunchedEffect(authState.isAuthenticated) {
        if (authState.isAuthenticated) {
            appViewModel.loadMyProfile()
        } else {
            appViewModel.clear()
        }
    }

    LaunchedEffect(authState.isAuthenticated, appState.me) {
        if (!authState.isAuthenticated) return@LaunchedEffect
        val me = appState.me ?: return@LaunchedEffect
        val current = navController.currentDestination?.route
        if (current in setOf(Routes.PROTECTED_FLOW, Routes.MONITOR_FLOW, Routes.PROFILE)) return@LaunchedEffect

        val hasProtected = me.roles.contains("Protected")
        val hasMonitor = me.roles.contains("Monitor")

        when {
            hasProtected && !hasMonitor ->
                navController.navigate(Routes.PROTECTED_FLOW) { popUpTo(0) }
            hasMonitor && !hasProtected ->
                navController.navigate(Routes.MONITOR_FLOW) { popUpTo(0) }
            else ->
                navController.navigate(Routes.ROLE_PICKER) { popUpTo(0) }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBack = navController.previousBackStackEntry != null &&
            currentRoute !in setOf(Routes.LOGIN, Routes.ROLE_PICKER, Routes.PROTECTED_FLOW, Routes.MONITOR_FLOW)

    val isAuthenticated = authState.isAuthenticated

    Scaffold(
        topBar = {
            AppTopBar(
                showBack = showBack,
                isAuthenticated = isAuthenticated,
                onBack = { navController.popBackStack() },
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Routes.LOGIN) { popUpTo(0) }
                },
                onProfile = {
                    navController.navigate(Routes.PROFILE)
                }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        val start = if (isAuthenticated) Routes.ROLE_PICKER else Routes.LOGIN
        NavHost(
            navController = navController,
            startDestination = start,
            modifier = Modifier.fillMaxSize().padding(innerPadding)
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
                ProtectedFlow(appViewModel = appViewModel, onSwitchToMonitor = { navController.navigate(Routes.MONITOR_FLOW) { popUpTo(0) } })
            }
            composable(Routes.MONITOR_FLOW) {
                MonitorFlow(appViewModel = appViewModel, onSwitchToProtected = { navController.navigate(Routes.PROTECTED_FLOW) { popUpTo(0) } })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    showBack: Boolean,
    isAuthenticated: Boolean,
    onBack: () -> Unit,
    onLogout: () -> Unit,
    onProfile: () -> Unit
) {
    TopAppBar(
        title = { 
            // Hide "SafetYSec" in TopAppBar when NOT authenticated (on login screen)
            if (isAuthenticated) {
                Text("SafetYSec") 
            }
        },
        navigationIcon = {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = {
            if (isAuthenticated) {
                IconButton(onClick = onProfile) {
                    Icon(Icons.Default.Person, contentDescription = "Profile")
                }
                IconButton(onClick = onLogout) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                }
            }
        }
    )
}
