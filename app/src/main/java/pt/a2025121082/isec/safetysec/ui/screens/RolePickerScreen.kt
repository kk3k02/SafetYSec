package pt.a2025121082.isec.safetysec.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Simple role/mode selection screen.
 *
 * Lets the user choose which UI flow to open:
 * - Protected mode
 * - Monitor mode
 *
 * The actual navigation logic is provided by the caller via callbacks.
 */
@Composable
fun RolePickerScreen(
    onGoProtected: () -> Unit,
    onGoMonitor: () -> Unit
) {
    Column(Modifier.padding(16.dp)) {
        Text("Choose mode")
        Spacer(Modifier.padding(12.dp))

        // Navigate to Protected flow
        Button(onClick = onGoProtected, modifier = Modifier.fillMaxWidth()) {
            Text("Protected")
        }

        Spacer(Modifier.padding(8.dp))

        // Navigate to Monitor flow
        Button(onClick = onGoMonitor, modifier = Modifier.fillMaxWidth()) {
            Text("Monitor")
        }
    }
}