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

@Composable
fun RolePickerScreen(
    onGoProtected: () -> Unit,
    onGoMonitor: () -> Unit
) {
    Column(Modifier.padding(16.dp)) {
        Text("Choose mode")

        Spacer(Modifier.padding(12.dp))

        Button(onClick = onGoProtected, modifier = Modifier.fillMaxWidth()) {
            Text("Protected")
        }

        Spacer(Modifier.padding(8.dp))

        Button(onClick = onGoMonitor, modifier = Modifier.fillMaxWidth()) {
            Text("Monitor")
        }

        Spacer(Modifier.padding(8.dp))

        Text("Tip: one user can have both profiles. This only changes what UI you see now.")
    }
}
