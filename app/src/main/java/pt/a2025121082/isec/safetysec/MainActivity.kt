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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
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
import pt.a2025121082.isec.safetysec.viewmodel.AuthViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            SafetYSecTheme {
                SafetYSecApp()
            }
        }
    }
}

private object Routes {
    // Auth
    const val Login = "login"
    const val Register = "register"
    const val ResetPassword = "reset_password"

    // After login
    const val RolePicker = "role_picker"

    // Flows (each contains its own bottom-nav)
    const val ProtectedFlow = "protected_flow"
    const val MonitorFlow = "monitor_flow"
}

@Composable
private fun SafetYSecApp(
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    LaunchedEffect(Unit) { authViewModel.refreshAuthState() }

    val isAuthenticated = authViewModel.uiState.isAuthenticated
    val startDestination = remember(isAuthenticated) {
        if (isAuthenticated) Routes.RolePicker else Routes.Login
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val canNavigateBack = navController.previousBackStackEntry != null &&
            currentRoute != Routes.Login &&
            currentRoute != Routes.RolePicker &&
            currentRoute != Routes.ProtectedFlow &&
            currentRoute != Routes.MonitorFlow

    Scaffold(
        topBar = {
            AppTopBar(
                title = "SafetYSec",
                showBack = canNavigateBack,
                onBack = { navController.popBackStack() },
                isAuthenticated = isAuthenticated,
                onLogout = {
                    authViewModel.logout()
                    navController.navigate(Routes.Login) {
                        popUpTo(0)
                        launchSingleTop = true
                    }
                }
            )
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->

        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(Routes.Login) {
                LoginScreen(
                    viewModel = authViewModel,
                    onNavigateToRegistration = { navController.navigate(Routes.Register) },
                    onLoginSuccess = {
                        navController.navigate(Routes.RolePicker) {
                            popUpTo(Routes.Login) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onNavigateToResetPassword = { navController.navigate(Routes.ResetPassword) }
                )
            }

            composable(Routes.Register) {
                RegistrationScreen(
                    viewModel = authViewModel,
                    onNavigateToLogin = {
                        navController.navigate(Routes.Login) {
                            popUpTo(Routes.Register) { inclusive = true }
                            launchSingleTop = true
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
                    onGoProtected = {
                        navController.navigate(Routes.ProtectedFlow) {
                            popUpTo(Routes.RolePicker) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onGoMonitor = {
                        navController.navigate(Routes.MonitorFlow) {
                            popUpTo(Routes.RolePicker) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            // Nested flows with their own bottom navigation
            composable(Routes.ProtectedFlow) {
                ProtectedFlow(
                    onLogout = {
                        authViewModel.logout()
                        navController.navigate(Routes.Login) {
                            popUpTo(0)
                            launchSingleTop = true
                        }
                    },
                    onSwitchToMonitor = {
                        navController.navigate(Routes.MonitorFlow) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = false
                            }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(Routes.MonitorFlow) {
                MonitorFlow(
                    onLogout = {
                        authViewModel.logout()
                        navController.navigate(Routes.Login) {
                            popUpTo(0)
                            launchSingleTop = true
                        }
                    },
                    onSwitchToProtected = {
                        navController.navigate(Routes.ProtectedFlow) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                inclusive = false
                            }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
    isAuthenticated: Boolean,
    onLogout: () -> Unit
) {
    TopAppBar(
        title = { Text(title) },
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
