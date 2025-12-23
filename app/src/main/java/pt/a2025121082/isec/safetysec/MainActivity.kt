package pt.a2025121082.isec.safetysec

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import pt.a2025121082.isec.safetysec.ui.auth.LoginScreen
import pt.a2025121082.isec.safetysec.ui.auth.RegistrationScreen
import pt.a2025121082.isec.safetysec.ui.theme.SafetYSecTheme
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

        setContent {
            SafetYSecTheme {
                SafetYSecApp()
            }
        }
    }
}

/**
 * Navigation route constants for the application.
 */
private object Routes {
    const val Login = "login"
    const val Register = "register"
    const val ProtectedDash = "protected_dashboard"
    const val MonitorDash = "monitor_dashboard"
    const val CombinedDash = "combined_dashboard"
}

/**
 * Root composable of the application.
 *
 * - Initializes NavController and AuthViewModel
 * - Refreshes auth state on app start (email verification status)
 * - Chooses start destination based on authentication state
 * - Defines navigation graph for auth screens and dashboards
 */
@Composable
private fun SafetYSecApp(
    navController: NavHostController = rememberNavController(),
    authViewModel: AuthViewModel = hiltViewModel()
) {
    // Refresh MFA status on app start (emailVerified may have changed outside the app).
    LaunchedEffect(Unit) {
        authViewModel.refreshAuthState()
    }

    val isAuthenticated = authViewModel.uiState.isAuthenticated

    // Decide initial screen based on auth state.
    val startDestination = if (isAuthenticated) Routes.ProtectedDash else Routes.Login

    Scaffold(
        topBar = {
            AppTopBar(
                isAuthenticated = isAuthenticated,
                onLogout = {
                    authViewModel.logout()
                    // Clear back stack and go to login.
                    navController.navigate(Routes.Login) {
                        popUpTo(0)
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
                        // After login, go to dashboard and remove login from back stack.
                        navController.navigate(Routes.ProtectedDash) {
                            popUpTo(Routes.Login) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.Register) {
                RegistrationScreen(
                    viewModel = authViewModel,
                    onNavigateToLogin = {
                        // After registration, return to login and remove register from back stack.
                        navController.navigate(Routes.Login) {
                            popUpTo(Routes.Register) { inclusive = true }
                        }
                    }
                )
            }

            // Placeholders for now — replace with real dashboards later.
            composable(Routes.ProtectedDash) {
                PlaceholderScreen(
                    title = "ProtectedDashboard",
                    subtitle = "Connect ProtectedDashboardScreen here"
                )
            }

            composable(Routes.MonitorDash) {
                PlaceholderScreen(
                    title = "MonitorDashboard",
                    subtitle = "Connect MonitorDashboardScreen here"
                )
            }

            composable(Routes.CombinedDash) {
                PlaceholderScreen(
                    title = "CombinedDashboard",
                    subtitle = "You can add TabRow: Protected / Monitor here"
                )
            }
        }
    }
}

/**
 * Top app bar shown across the app.
 *
 * Displays a Logout action only when the user is authenticated.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppTopBar(
    isAuthenticated: Boolean,
    onLogout: () -> Unit
) {
    TopAppBar(
        title = { Text("SafetYSec") },
        actions = {
            if (isAuthenticated) {
                IconButton(onClick = onLogout) {
                    Icon(Icons.Filled.Logout, contentDescription = "Logout")
                }
            }
        }
    )
}

/**
 * Simple placeholder screen used until real dashboard screens are implemented.
 */
@Composable
private fun PlaceholderScreen(title: String, subtitle: String) {
    Surface {
        ListItem(
            headlineContent = { Text(title) },
            supportingContent = { Text(subtitle) }
        )
    }
}

/**
 * Generic loading screen (can be used while fetching remote data).
 */
@Composable
private fun LoadingScreen() {
    Surface {
        ListItem(
            headlineContent = { Text("Loading...") },
            supportingContent = { LinearProgressIndicator() }
        )
    }
}

/**
 * Generic error screen (can be used to display a blocking error state).
 */
@Composable
private fun ErrorScreen(message: String) {
    Surface {
        ListItem(
            headlineContent = { Text("Error") },
            supportingContent = { Text(message) }
        )
    }
}
