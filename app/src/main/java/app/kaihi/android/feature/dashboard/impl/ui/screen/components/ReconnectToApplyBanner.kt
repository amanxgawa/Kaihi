package app.kaihi.android.feature.dashboard.impl.ui.screen.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.kaihi.android.R
import app.kaihi.android.platform.ui.theme.size.dimens

@Composable
internal fun ReconnectToApplyBanner(
    onReconnect: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val dimens = MaterialTheme.dimens

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.m, vertical = dimens.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.s),
        ) {
            Text(
                text = stringResource(R.string.dashboard_reconnect_to_apply_message),
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(
                onClick = onReconnect,
                enabled = enabled,
            ) {
                Text(text = stringResource(R.string.dashboard_reconnect_to_apply_action))
            }
        }
    }
}
