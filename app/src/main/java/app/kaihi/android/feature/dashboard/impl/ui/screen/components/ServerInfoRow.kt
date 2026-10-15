package app.kaihi.android.feature.dashboard.impl.ui.screen.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.kaihi.android.platform.ui.theme.JetBrainsMono

// Plain String, not UiText: nothing here crosses the MVI-state boundary.
@Composable
internal fun ServerInfoRow(label: String, value: String, modifier: Modifier = Modifier, monospace: Boolean = false) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = if (monospace) {
                MaterialTheme.typography.bodyMedium.copy(fontFamily = JetBrainsMono)
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
