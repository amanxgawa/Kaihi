package app.kaihi.android.feature.settings.impl.ui.screen.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.kaihi.android.R
import app.kaihi.android.platform.ui.theme.size.dimens

@Composable
internal fun SettingsSubscriptionsSection(
    effectiveUserAgent: String,
    onUserAgentClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = MaterialTheme.dimens

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(dimens.m)) {
        Text(
            text = stringResource(id = R.string.settings_subscriptions_section_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SettingsActionRow(
            title = stringResource(id = R.string.settings_subscription_user_agent_title),
            description = effectiveUserAgent,
            onClick = onUserAgentClick,
        )
    }
}
