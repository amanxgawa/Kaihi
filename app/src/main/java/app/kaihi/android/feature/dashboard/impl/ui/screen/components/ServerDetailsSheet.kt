package app.kaihi.android.feature.dashboard.impl.ui.screen.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.kaihi.android.R
import app.kaihi.android.core.vpn.spec.XrayFingerprint
import app.kaihi.android.feature.dashboard.impl.ui.model.ServerDetailsUi
import app.kaihi.android.platform.ui.components.ConfirmDialog
import app.kaihi.android.platform.ui.icons.KaihiIcons
import app.kaihi.android.platform.ui.theme.JetBrainsMono
import app.kaihi.android.platform.ui.theme.size.dimens
import app.kaihi.android.platform.ui.util.UiText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServerDetailsSheet(
    details: ServerDetailsUi,
    isAdvancedMode: Boolean,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onForget: () -> Unit,
    onFingerprintChanged: (XrayFingerprint) -> Unit,
    onMuxEnabledChanged: (Boolean) -> Unit,
    onMuxConcurrencyChanged: (Int) -> Unit,
) {
    val dimens = MaterialTheme.dimens
    var showForgetConfirm by rememberSaveable(details.id) { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = dimens.l, end = dimens.l, bottom = dimens.l),
            verticalArrangement = Arrangement.spacedBy(dimens.l),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.l),
            ) {
                ServerFlag(flag = details.flag)
                Column {
                    Text(
                        text = details.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = details.stack,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(dimens.s)) {
                ServerInfoRow(
                    label = stringResource(R.string.dashboard_server_info_server),
                    value = details.address,
                    monospace = true,
                )
                details.maskingAs?.let { maskingAs ->
                    ServerInfoRow(
                        label = stringResource(R.string.dashboard_server_info_masking_as),
                        value = maskingAs,
                        monospace = true,
                    )
                }
                ServerInfoRow(
                    label = stringResource(R.string.dashboard_server_info_deployed),
                    value = details.deployedAtMillis.toDisplayDate(),
                )
            }

            FilledTonalButton(
                onClick = onShare,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                Icon(
                    imageVector = KaihiIcons.Outlined.Share,
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(text = stringResource(R.string.dashboard_export_link_label))
            }

            if (isAdvancedMode) {
                HorizontalDivider()
                ServerAdvancedSection(
                    fingerprint = details.fingerprint,
                    muxEnabled = details.muxEnabled,
                    muxConcurrency = details.muxConcurrency,
                    onFingerprintChanged = onFingerprintChanged,
                    onMuxEnabledChanged = onMuxEnabledChanged,
                    onMuxConcurrencyChanged = onMuxConcurrencyChanged,
                )
            }

            TextButton(
                onClick = { showForgetConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(text = stringResource(R.string.dashboard_server_forget))
            }
        }
    }

    if (showForgetConfirm) {
        ConfirmDialog(
            title = UiText.Resource(R.string.dashboard_server_forget_confirm_title),
            body = UiText.Resource(R.string.dashboard_server_forget_confirm_body),
            onConfirm = {
                showForgetConfirm = false
                onForget()
            },
            onDismiss = { showForgetConfirm = false },
        )
    }
}
