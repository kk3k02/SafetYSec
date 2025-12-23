package pt.a2025121082.isec.safetysec.ui.protected

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ProtectedHistoryScreen() {
    Column(Modifier.padding(16.dp)) {
        Text("Alert history (Protected)")
        Spacer(Modifier.padding(8.dp))
        Card {
            ListItem(
                headlineContent = { Text("Fall detected") },
                supportingContent = { Text("2025-12-23 15:10 • cancelled: no") }
            )
        }
    }
}

@Composable
fun ProtectedTimeWindowsScreen() {
    Column(Modifier.padding(16.dp)) {
        Text("Time observation windows")
        Spacer(Modifier.padding(8.dp))
        Card {
            ListItem(
                headlineContent = { Text("Mon–Fri") },
                supportingContent = { Text("09:00 – 18:00") }
            )
        }
        Spacer(Modifier.padding(8.dp))
        Button(onClick = { /* TODO add window */ }, modifier = Modifier.fillMaxWidth()) {
            Text("Add window")
        }
    }
}

@Composable
fun ProtectedMonitorsScreen() {
    var otp by remember { mutableStateOf("") }

    Column(Modifier.padding(16.dp)) {
        Text("Authorized monitors")
        Spacer(Modifier.padding(8.dp))
        Card {
            ListItem(
                headlineContent = { Text("Monitor: John Doe") },
                supportingContent = { Text("Rules authorized: 3") }
            )
        }

        Spacer(Modifier.padding(16.dp))
        Text("Add monitor (OTP)")
        Spacer(Modifier.padding(8.dp))

        OutlinedTextField(
            value = otp,
            onValueChange = { otp = it },
            label = { Text("One-time code") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.padding(8.dp))
        Button(onClick = { /* TODO confirm OTP */ }, modifier = Modifier.fillMaxWidth()) {
            Text("Associate monitor")
        }
    }
}

@Composable
fun ProtectedProfileScreen(
    onLogout: () -> Unit,
    onSwitchToMonitor: () -> Unit
) {
    var cancelCode by remember { mutableStateOf("") }

    Column(Modifier.padding(16.dp)) {
        Text("Profile (Protected)")
        Spacer(Modifier.padding(12.dp))

        OutlinedTextField(
            value = cancelCode,
            onValueChange = { cancelCode = it },
            label = { Text("Alert cancellation code") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.padding(8.dp))
        Button(onClick = { /* TODO save */ }, modifier = Modifier.fillMaxWidth()) {
            Text("Save")
        }

        Spacer(Modifier.padding(12.dp))
        Button(onClick = onSwitchToMonitor, modifier = Modifier.fillMaxWidth()) {
            Text("Switch to Monitor UI")
        }

        Spacer(Modifier.padding(8.dp))
        Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) {
            Text("Logout")
        }
    }
}
