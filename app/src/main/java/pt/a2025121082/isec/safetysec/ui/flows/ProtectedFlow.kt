package pt.a2025121082.isec.safetysec.ui.flows

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import pt.a2025121082.isec.safetysec.ui.protected.ProtectedCancelAlertDialog
import pt.a2025121082.isec.safetysec.ui.protected.ProtectedHistoryScreen
import pt.a2025121082.isec.safetysec.ui.protected.ProtectedMonitorsAndRulesScreen
import pt.a2025121082.isec.safetysec.ui.protected.ProtectedProfileScreen
import pt.a2025121082.isec.safetysec.ui.protected.ProtectedWindowsScreen
import pt.a2025121082.isec.safetysec.viewmodel.AppViewModel

/**
 * Navigation flow for the Protected role.
 *
 * Provides:
 * - Bottom navigation with four tabs:
 *   - History (alerts/events history)
 *   - Windows (time windows when rules can be active)
 *   - Monitors (linked monitors + rule authorization)
 *   - Profile (account + switching roles)
 * - A Panic floating action button that triggers an immediate PANIC alert
 * - A mandatory 10-second cancel dialog after an alert is triggered (project requirement)
 */
@Composable
fun ProtectedFlow(
    appViewModel: AppViewModel,
    onSwitchToMonitor: () -> Unit
) {
    /** Local NavController used only inside the Protected flow. */
    val nav = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                // Observe navigation destination to highlight the selected tab.
                val entry by nav.currentBackStackEntryAsState()
                val dest = entry?.destination

                /**
                 * Navigates to the selected route while preserving/restoring state
                 * between bottom navigation tabs.
                 */
                fun go(route: String) {
                    nav.navigate(route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        restoreState = true
                        launchSingleTop = true
                    }
                }

                NavigationBarItem(
                    selected = dest?.route == PRoutes.History,
                    onClick = { go(PRoutes.History) },
                    icon = { Icon(Icons.Filled.History, contentDescription = "History") },
                    label = { Text("History") }
                )

                NavigationBarItem(
                    selected = dest?.route == PRoutes.Windows,
                    onClick = { go(PRoutes.Windows) },
                    icon = { Icon(Icons.Filled.Schedule, contentDescription = "Windows") },
                    label = { Text("Windows") }
                )

                NavigationBarItem(
                    selected = dest?.route == PRoutes.Monitors,
                    onClick = { go(PRoutes.Monitors) },
                    icon = { Icon(Icons.Filled.Security, contentDescription = "Monitors") },
                    label = { Text("Monitors") }
                )

                NavigationBarItem(
                    selected = dest?.route == PRoutes.Profile,
                    onClick = { go(PRoutes.Profile) },
                    icon = { Icon(Icons.Filled.Person, contentDescription = "Profile") },
                    label = { Text("Profile") }
                )
            }
        },

        // Panic button (manual alert trigger)
        floatingActionButton = {
            FloatingActionButton(onClick = { appViewModel.triggerPanic() }) {
                Icon(Icons.Filled.Warning, contentDescription = "Panic")
            }
        }
    ) { innerPadding ->
        // Host navigation destinations for the Protected flow.
        NavHost(
            navController = nav,
            startDestination = PRoutes.History
        ) {
            composable(PRoutes.History) { ProtectedHistoryScreen(appViewModel) }
            composable(PRoutes.Windows) { ProtectedWindowsScreen(appViewModel) }
            composable(PRoutes.Monitors) { ProtectedMonitorsAndRulesScreen(appViewModel) }
            composable(PRoutes.Profile) { ProtectedProfileScreen(appViewModel, onSwitchToMonitor) }
        }

        // 10-second cancel dialog shown after an alert is triggered (project requirement).
        ProtectedCancelAlertDialog(appViewModel)
    }
}

/**
 * Route constants for Protected flow destinations.
 */
private object PRoutes {
    const val History = "p_history"
    const val Windows = "p_windows"
    const val Monitors = "p_monitors"
    const val Profile = "p_profile"
}