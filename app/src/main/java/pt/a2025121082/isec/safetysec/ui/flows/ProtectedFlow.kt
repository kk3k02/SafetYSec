package pt.a2025121082.isec.safetysec.ui.flows

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*

import pt.a2025121082.isec.safetysec.ui.protected.*

private object ProtectedRoutes {
    const val History = "p_history"
    const val TimeWindows = "p_time_windows"
    const val Monitors = "p_monitors"
    const val Profile = "p_profile"
}

@Composable
fun ProtectedFlow(
    onLogout: () -> Unit,
    onSwitchToMonitor: () -> Unit
) {
    val navController = rememberNavController()
    val items = listOf(
        ProtectedRoutes.History to Pair("History", Icons.Filled.History),
        ProtectedRoutes.TimeWindows to Pair("Time windows", Icons.Filled.Schedule),
        ProtectedRoutes.Monitors to Pair("Monitors", Icons.Filled.Security),
        ProtectedRoutes.Profile to Pair("Profile", Icons.Filled.Person),
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                items.forEach { (route, meta) ->
                    val (label, icon) = meta
                    NavigationBarItem(
                        selected = currentDestination?.hierarchy?.any { it.route == route } == true,
                        onClick = {
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { inner ->
        NavHost(
            navController = navController,
            startDestination = ProtectedRoutes.History,
            modifier = Modifier.padding(inner)
        ) {
            composable(ProtectedRoutes.History) {
                ProtectedHistoryScreen()
            }
            composable(ProtectedRoutes.TimeWindows) {
                ProtectedTimeWindowsScreen()
            }
            composable(ProtectedRoutes.Monitors) {
                ProtectedMonitorsScreen()
            }
            composable(ProtectedRoutes.Profile) {
                ProtectedProfileScreen(
                    onLogout = onLogout,
                    onSwitchToMonitor = onSwitchToMonitor
                )
            }
        }
    }
}
