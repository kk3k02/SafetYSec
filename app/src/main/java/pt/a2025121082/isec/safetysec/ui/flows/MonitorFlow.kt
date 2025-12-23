package pt.a2025121082.isec.safetysec.ui.flows

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.*

import pt.a2025121082.isec.safetysec.ui.monitor.*

private object MonitorRoutes {
    const val Dashboard = "m_dashboard"
    const val ProtectedList = "m_protected_list"
    const val Stats = "m_stats"
    const val Profile = "m_profile"
}

@Composable
fun MonitorFlow(
    onLogout: () -> Unit,
    onSwitchToProtected: () -> Unit
) {
    val navController = rememberNavController()
    val items = listOf(
        MonitorRoutes.Dashboard to Pair("Dashboard", Icons.Filled.Warning),
        MonitorRoutes.ProtectedList to Pair("Protected", Icons.Filled.Group),
        MonitorRoutes.Stats to Pair("Stats", Icons.Filled.BarChart),
        MonitorRoutes.Profile to Pair("Profile", Icons.Filled.Person),
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
            startDestination = MonitorRoutes.Dashboard,
            modifier = Modifier.padding(inner)
        ) {
            composable(MonitorRoutes.Dashboard) { MonitorDashboardScreen() }
            composable(MonitorRoutes.ProtectedList) { MonitorProtectedListScreen() }
            composable(MonitorRoutes.Stats) { MonitorStatsScreen() }
            composable(MonitorRoutes.Profile) {
                MonitorProfileScreen(
                    onLogout = onLogout,
                    onSwitchToProtected = onSwitchToProtected
                )
            }
        }
    }
}