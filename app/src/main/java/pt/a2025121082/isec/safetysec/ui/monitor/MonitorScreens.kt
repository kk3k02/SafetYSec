package pt.a2025121082.isec.safetysec.ui.monitor

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun MonitorDashboardScreen() {
    Column(Modifier.padding(16.dp)) {
        Text("Monitor dashboard")
        Spacer(Modifier.padding(8.dp))
        Card {
            ListItem(
                headlineContent = { Text("Recent alert: Accident detected") },
                supportingContent = { Text("Protected: Alice • 2025-12-23 15:02 • Location: GPS(...)") }
            )
        }
    }
}

@Composable
fun MonitorProtectedListScreen() {
    Column(Modifier.padding(16.dp)) {
        Text("Protected individuals")
        Spacer(Modifier.padding(8.dp))
        Card {
            ListItem(
                headlineContent = { Text("Alice (OK)") },
                supportingContent = { Text("Last seen: 2 min ago") }
            )
        }
        Spacer(Modifier.padding(8.dp))
        Button(onClick = { /* TODO generate OTP */ }, modifier = Modifier.fillMaxWidth()) {
            Text("Associate protected (generate OTP)")
        }
    }
}

@Composable
fun MonitorStatsScreen() {
    Column(Modifier.padding(16.dp)) {
        Text("Statistics")
        Spacer(Modifier.padding(8.dp))
        Card {
            ListItem(
                headlineContent = { Text("Alerts last 7 days") },
                supportingContent = { Text("Falls: 2 • Accidents: 0 • Speed: 5") }
            )
        }
    }
}

@Composable
fun MonitorProfileScreen(
    onLogout: () -> Unit,
    onSwitchToProtected: () -> Unit
) {
    Column(Modifier.padding(16.dp)) {
        Text("Profile (Monitor)")
        Spacer(Modifier.padding(12.dp))

        Button(onClick = { /* TODO change password */ }, modifier = Modifier.fillMaxWidth()) {
            Text("Change password")
        }

        Spacer(Modifier.padding(8.dp))
        Button(onClick = onSwitchToProtected, modifier = Modifier.fillMaxWidth()) {
            Text("Switch to Protected UI")
        }

        Spacer(Modifier.padding(8.dp))
        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text("Logout")
        }
    }
}
