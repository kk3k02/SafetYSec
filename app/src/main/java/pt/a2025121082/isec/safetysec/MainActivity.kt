package pt.a2025121082.isec.safetysec

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Logout
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
import pt.a2025121082.isec.safetysec.ui.screens.RolePickerScreen
import pt.a2025121082.isec.safetysec.ui.theme.SafetYSecTheme
import pt.a2025121082.isec.safetysec.viewmodel.AppViewModel
import pt.a2025121082.isec.safetysec.viewmodel.AuthViewModel

/**
 * Main entry Activity for the app.
 *
 * - Enables edge-to-edge layout
 * - Hosts the Compose UI tree
 * - Uses Hilt for dependency injection (@AndroidEntryPoint)
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SafetYSecTheme { SafetYSecApp() } }
    }
}

/**
 * Navigation route constants used by the root NavHost.
 */
private object Routes {
    // Auth
    const val Login = "login"
    const val Register = "register"
    const val ResetPassword = "reset_password"

    // Role + flows
    const val RolePicker = "role_picker"
    const val ProtectedFlow = "protected_flow"
    const val MonitorFlow = "monitor_flow"
}

/**
 * Root composable of the application.
 *
 * Responsibilities:
 * - Refresh auth status on app start (including email verification)
 * - Load Firestore profile after authentication (roles, associations)
 * - Auto-route to the correct flow based on roles:
 *   - Protected only -> ProtectedFlow
 *   - Monitor only -> MonitorFlow
 *   - Both roles -> RolePicker
 * - Hosts the main NavHost for auth + role flows
 * - Provides a top app bar with optional Back + Logout actions
 */
@Composable
private fun SafetYSecApp(
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = hiltViewModel(),
    appViewModel: AppViewModel = hiltViewModel()
) {
    // Refresh MFA/auth state when the app starts (emailVerified might have changed).
    LaunchedEffect(Unit) { authViewModel.refreshAuthState() }

    // When authenticated -> load Firestore profile; otherwise reset app state.
    LaunchedEffect(authViewModel.uiState.isAuthenticated) {
        if (authViewModel.uiState.isAuthenticated) {
            appViewModel.loadMyProfile()
        } else {
            appViewModel.clear()
        }
    }

    // Auto-route once profile is loaded (role-based navigation).
    LaunchedEffect(authViewModel.uiState.isAuthenticated, appViewModel.state.me) {
        if (!authViewModel.uiState.isAuthenticated) return@LaunchedEffect
        val me = appViewModel.state.me ?: return@LaunchedEffect

        val hasProtected = me.roles.contains("Protected")
        val hasMonitor = me.roles.contains("Monitor")

        when {
            hasProtected && !hasMonitor ->
                navController.navigate(Routes.ProtectedFlow) { popUpTo(0) }
            hasMonitor && !hasProtected ->
                navController.navigate(Routes.MonitorFlow) { popUpTo(0) }
            hasProtected && hasMonitor ->
                navController.navigate(Routes.RolePicker) { popUpTo(0) }
            else ->
                navController.navigate(Routes.RolePicker) { popUpTo(0) }
        }
    }

    // Track current route to decide if the Back button should be shown.
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    /**
     * Show a Back button when:
     * - there is a previous destination in the back stack
     * - we are not on the "root" screens (login / role picker / main flows)
     */
    val showBack = navController.previousBackStackEntry != null &&
            currentRoute !in setOf(
        Routes.Login,
        Routes.RolePicker,
        Routes.ProtectedFlow,
        Routes.MonitorFlow
    )

    /** Whether the user is currently authenticated. */
    val isAuthenticated = authViewModel.uiState.isAuthenticated

    Scaffold(
        topBar = {
            AppTopBar(
                showBack = showBack,
                isAuthenticated = isAuthenticated,
                onBack = { navController.popBackStack() },
                onLogout = {
                    authViewModel.logout()
                    // Clear the entire back stack and return to Login.
                    navController.navigate(Routes.Login) { popUpTo(0) }
                }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->

        // Initial destination for the root NavHost depends on auth state.
        val start = if (isAuthenticated) Routes.RolePicker else Routes.Login

        NavHost(
            navController = navController,
            startDestination = start,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(Routes.Login) {
                LoginScreen(
                    viewModel = authViewModel,
                    onNavigateToRegistration = { navController.navigate(Routes.Register) },
                    onNavigateToResetPassword = { navController.navigate(Routes.ResetPassword) },
                    onLoginSuccess = {
                        // No-op: role-based routing happens via LaunchedEffect after profile load.
                    }
                )
            }

            composable(Routes.Register) {
                RegistrationScreen(
                    viewModel = authViewModel,
                    onNavigateToLogin = {
                        navController.navigate(Routes.Login) {
                            popUpTo(Routes.Register) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.ResetPassword) {
                PasswordResetScreen(
                    authViewModel = authViewModel,
                    onDone = { navController.popBackStack() }
                )
            }

            composable(Routes.RolePicker) {
                RolePickerScreen(
                    onGoProtected = { navController.navigate(Routes.ProtectedFlow) { popUpTo(0) } },
                    onGoMonitor = { navController.navigate(Routes.MonitorFlow) { popUpTo(0) } }
                )
            }

            composable(Routes.ProtectedFlow) {
                ProtectedFlow(
                    appViewModel = appViewModel,
                    onSwitchToMonitor = {
                        navController.navigate(Routes.MonitorFlow) { popUpTo(0) }
                    }
                )
            }

            composable(Routes.MonitorFlow) {
                MonitorFlow(
                    appViewModel = appViewModel,
                    onSwitchToProtected = {
                        navController.navigate(Routes.ProtectedFlow) { popUpTo(0) }
                    }
                )
            }
        }
    }
}

/**
 * Top app bar shown across the app.
 *
 * - Shows an optional Back button depending on navigation state
 * - Shows a Logout action only when the user is authenticated
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    showBack: Boolean,
    isAuthenticated: Boolean,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    TopAppBar(
        title = { Text("SafetYSec") },
        navigationIcon = {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = {
            if (isAuthenticated) {
                IconButton(onClick = onLogout) {
                    Icon(Icons.Filled.Logout, contentDescription = "Logout")
                }
            }
        }
    )
}