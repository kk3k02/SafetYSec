package pt.a2025121082.isec.safetysec.ui.flows

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import pt.a2025121082.isec.safetysec.data.model.RuleType
import pt.a2025121082.isec.safetysec.viewmodel.AppViewModel
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectedFlow(
    appViewModel: AppViewModel,
    onSwitchToMonitor: () -> Unit,
    onLogout: () -> Unit,
    onProfile: () -> Unit
) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route
    val canTriggerPanic = appViewModel.state.monitorRuleBundles.any { bundle ->
        bundle.authorizedTypes.contains(RuleType.PANIC)
    }
    val showClearDialog = remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) { appViewModel.setActiveMode(pt.a2025121082.isec.safetysec.viewmodel.AppMode.PROTECTED) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val title = when (currentRoute) {
                        PRoutes.History -> "Alert History"
                        PRoutes.Windows -> "Time Windows"
                        PRoutes.Monitors -> "Monitors"
                        PRoutes.Profile -> "Profile Settings"
                        else -> "SafetYSec"
                    }
                    Text(title)
                },
                actions = {
                    if (currentRoute == PRoutes.History) {
                        IconButton(
                            onClick = { showClearDialog.value = true },
                            enabled = appViewModel.state.myAlerts.isNotEmpty()
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear history")
                        }
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
        floatingActionButton = {
            if (currentRoute != PRoutes.Windows) {
                FloatingActionButton(
                    onClick = { if (canTriggerPanic) appViewModel.triggerPanic() },
                    containerColor = if (canTriggerPanic) {
                        FloatingActionButtonDefaults.containerColor
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = "Panic")
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            NavHost(
                navController = nav,
                startDestination = PRoutes.History
            ) {
                composable(PRoutes.History) { ProtectedHistoryScreen(appViewModel) }
                composable(PRoutes.Windows) { ProtectedWindowsScreen(appViewModel) }
                composable(PRoutes.Monitors) { ProtectedMonitorsAndRulesScreen(appViewModel) }
                composable(PRoutes.Profile) { ProtectedProfileScreen(appViewModel, onSwitchToMonitor) }
            }
        }

        ProtectedCancelAlertDialog(appViewModel)
    }

    if (showClearDialog.value) {
        AlertDialog(
            onDismissRequest = { showClearDialog.value = false },
            title = { Text("Clear history") },
            text = { Text("This will delete all recent alerts from your history.") },
            confirmButton = {
                TextButton(onClick = {
                    appViewModel.clearMyAlertsHistory()
                    Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
                    showClearDialog.value = false
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog.value = false }) { Text("Cancel") }
            }
        )
    }
}

private object PRoutes {
    const val History = "p_history"
    const val Windows = "p_windows"
    const val Monitors = "p_monitors"
    const val Profile = "p_profile"
}
