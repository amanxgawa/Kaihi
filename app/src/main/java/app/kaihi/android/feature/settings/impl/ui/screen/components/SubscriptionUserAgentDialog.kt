package app.kaihi.android.feature.settings.impl.ui.screen.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.kaihi.android.R
import app.kaihi.android.platform.ui.components.KaihiTextField
import app.kaihi.android.platform.ui.theme.size.dimens
import app.kaihi.android.platform.ui.util.UiText
import app.kaihi.android.platform.ui.util.asString

@Composable
internal fun SubscriptionUserAgentDialog(
    draft: String,
    defaultUserAgent: String,
    error: UiText?,
    saveEnabled: Boolean,
    onDraftChanged: (String) -> Unit,
    onSave: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val dimens = MaterialTheme.dimens

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.settings_subscription_user_agent_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.s)) {
                KaihiTextField(
                    value = draft,
                    onValueChange = onDraftChanged,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = UiText.Raw(defaultUserAgent),
                    singleLine = true,
                )
                if (error != null) {
                    Text(
                        text = error.asString(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onReset) {
                    Text(text = stringResource(R.string.settings_subscription_user_agent_reset))
                }
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.settings_subscription_user_agent_cancel))
                }
                TextButton(onClick = onSave, enabled = saveEnabled) {
                    Text(text = stringResource(R.string.settings_subscription_user_agent_save))
                }
            }
        },
    )
}
