package pt.a2025121082.isec.safetysec.ui.flows

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Tune
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
import pt.a2025121082.isec.safetysec.ui.monitor.MonitorDashboardScreen
import pt.a2025121082.isec.safetysec.ui.monitor.MonitorLinkScreen
import pt.a2025121082.isec.safetysec.ui.monitor.MonitorProfileScreen
import pt.a2025121082.isec.safetysec.ui.monitor.MonitorRulesScreen
import pt.a2025121082.isec.safetysec.viewmodel.AppViewModel

/**
 * Navigation flow for the Monitor role.
 *
 * Provides a bottom navigation bar with four tabs:
 * - Dashboard
 * - Link (association with Protected users)
 * - Rules (configure / request monitoring rules)
 * - Profile (role switching, account actions, etc.)
 *
 * This flow uses a dedicated NavController scoped to the Monitor section.
 */
@Composable
fun MonitorFlow(
    appViewModel: AppViewModel,
    onSwitchToProtected: () -> Unit
) {
    /** Local NavController used only inside the Monitor flow. */
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
    ) { _ ->
        // Host navigation destinations for the Monitor flow.
        NavHost(navController = nav, startDestination = MRoutes.Dash) {
            composable(MRoutes.Dash) { MonitorDashboardScreen(appViewModel) }
            composable(MRoutes.Link) { MonitorLinkScreen(appViewModel) }
            composable(MRoutes.Rules) { MonitorRulesScreen(appViewModel) }
            composable(MRoutes.Profile) { MonitorProfileScreen(onSwitchToProtected) }
        }
    }
}

/**
 * Route constants for Monitor flow destinations.
 */
private object MRoutes {
    const val Dash = "m_dash"
    const val Link = "m_link"
    const val Rules = "m_rules"
    const val Profile = "m_profile"
}