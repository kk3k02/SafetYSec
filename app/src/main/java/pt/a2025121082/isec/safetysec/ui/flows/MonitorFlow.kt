package pt.a2025121082.isec.safetysec.ui.flows

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = when (currentRoute) {
                        MRoutes.Dash -> "Monitor Dashboard"
                        MRoutes.Link -> "Link Accounts"
                        MRoutes.Rules -> "Monitoring Rules"
                        else -> "SafetYSec"
                    }
                    Text(title)
                },
                actions = {
                    IconButton(onClick = onProfile) {
                        Icon(Icons.Default.Person, contentDescription = "Profile")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                val dest = entry?.destination
                fun go(route: String) {
                    nav.navigate(route) {
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
        NavHost(
            navController = nav, 
            startDestination = MRoutes.Dash,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(MRoutes.Dash) { MonitorDashboardScreen(appViewModel) }
            composable(MRoutes.Link) { MonitorLinkScreen(appViewModel) }
            composable(MRoutes.Rules) { MonitorRulesScreen(appViewModel) }
            composable(MRoutes.Profile) { MonitorProfileScreen(onSwitchToProtected) }
        }
    }
}

private object MRoutes {
    const val Dash = "m_dash"
    const val Link = "m_link"
    const val Rules = "m_rules"
    const val Profile = "m_profile"
}
