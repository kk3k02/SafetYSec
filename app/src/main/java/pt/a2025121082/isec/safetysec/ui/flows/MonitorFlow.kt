package pt.a2025121082.isec.safetysec.ui.flows

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import pt.a2025121082.isec.safetysec.ui.monitor.MonitorDashboardScreen
import pt.a2025121082.isec.safetysec.ui.monitor.MonitorLinkScreen
import pt.a2025121082.isec.safetysec.ui.monitor.MonitorProfileScreen
import pt.a2025121082.isec.safetysec.ui.monitor.MonitorRulesScreen
import pt.a2025121082.isec.safetysec.viewmodel.AppViewModel

/**
 * Main navigation flow for users in the "Monitor" role.
 * This composable manages internal navigation for supervisors, 
 * including the dashboard, account linking, and rule configuration.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorFlow(
    appViewModel: AppViewModel,
    onSwitchToProtected: () -> Unit,
    onLogout: () -> Unit,
    onProfile: () -> Unit
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route

    // Set the application mode to MONITOR when this flow is active
    LaunchedEffect(Unit) { 
        appViewModel.setActiveMode(pt.a2025121082.isec.safetysec.viewmodel.AppMode.MONITOR) 
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    // Update TopAppBar title based on the active destination
                    val title = when (currentRoute) {
                        MRoutes.Dash -> "Monitor Dashboard"
                        MRoutes.Link -> "Link Accounts"
                        MRoutes.Rules -> "Monitoring Rules"
                        MRoutes.Profile -> "Profile Settings"
                        else -> "SafetYSec"
                    }
                    Text(title)
                },
                actions = {
                    // Global logout button
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                    }
                }
            )
        },
        bottomBar = {
            // Main navigation tabs for the Monitor flow
            NavigationBar {
                val dest = entry?.destination
                fun go(route: String) {
                    nav.navigate(route) {
                        // Avoid building up a large stack of destinations
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        restoreState = true
                        launchSingleTop = true
                    }
                }

                NavigationBarItem(
                    selected = dest?.route == MRoutes.Dash,
                    onClick = { go(MRoutes.Dash) },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "Dashboard") },
                    label = { Text("Dashboard") }
                )

                NavigationBarItem(
                    selected = dest?.route == MRoutes.Link,
                    onClick = { go(MRoutes.Link) },
                    icon = { Icon(Icons.Filled.Group, contentDescription = "Link") },
                    label = { Text("Link") }
                )

                NavigationBarItem(
                    selected = dest?.route == MRoutes.Rules,
                    onClick = { go(MRoutes.Rules) },
                    icon = { Icon(Icons.Filled.Tune, contentDescription = "Rules") },
                    label = { Text("Rules") }
                )

                NavigationBarItem(
                    selected = dest?.route == MRoutes.Profile,
                    onClick = { go(MRoutes.Profile) },
                    icon = { Icon(Icons.Filled.Person, contentDescription = "Profile") },
                    label = { Text("Profile") }
                )
            }
        }
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding)) {
            // Internal Navigation Host for Monitor-specific screens
            NavHost(navController = nav, startDestination = MRoutes.Dash) {
                composable(MRoutes.Dash) { MonitorDashboardScreen(appViewModel) }
                composable(MRoutes.Link) { MonitorLinkScreen(appViewModel) }
                composable(MRoutes.Rules) { MonitorRulesScreen(appViewModel) }
                composable(MRoutes.Profile) { MonitorProfileScreen(onSwitchToProtected) }
            }
        }
    }
}

/**
 * Route constants for the Monitor user flow.
 */
private object MRoutes {
    const val Dash = "m_dash"
    const val Link = "m_link"
    const val Rules = "m_rules"
    const val Profile = "m_profile"
}
