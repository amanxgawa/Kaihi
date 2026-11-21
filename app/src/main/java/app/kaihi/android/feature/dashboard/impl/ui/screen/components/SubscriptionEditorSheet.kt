package app.kaihi.android.feature.dashboard.impl.ui.screen.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import app.kaihi.android.R
import app.kaihi.android.feature.dashboard.impl.ui.model.SubscriptionEditorUi
import app.kaihi.android.platform.ui.components.ConfirmDialog
import app.kaihi.android.platform.ui.components.KaihiTextField
import app.kaihi.android.platform.ui.icons.KaihiIcons
import app.kaihi.android.platform.ui.theme.dimens
import app.kaihi.android.platform.ui.util.UiText
import java.util.Date

private const val MAX_NAME_LENGTH = 40

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubscriptionEditorSheet(
    editor: SubscriptionEditorUi,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val dimens = MaterialTheme.dimens
    val context = LocalContext.current
    var name by rememberSaveable(editor.id) { mutableStateOf(editor.name) }
    var showDeleteConfirm by rememberSaveable(editor.id) { mutableStateOf(false) }

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
            Text(
                text = stringResource(
                    if (editor.isRemote) {
                        R.string.dashboard_subscription_info_title
                    } else {
                        R.string.dashboard_subscription_edit_title
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            val trafficValue = when {
                editor.usedBytes != null && editor.totalBytes != null -> stringResource(
                    R.string.dashboard_subscription_usage_with_total,
                    Formatter.formatShortFileSize(context, editor.usedBytes),
                    Formatter.formatShortFileSize(context, editor.totalBytes),
                )
                editor.usedBytes != null -> stringResource(
                    R.string.dashboard_subscription_usage_used,
                    Formatter.formatShortFileSize(context, editor.usedBytes),
                )
                editor.totalBytes != null -> stringResource(
                    R.string.dashboard_subscription_usage_total,
                    Formatter.formatShortFileSize(context, editor.totalBytes),
                )
                else -> null
            }
            val expiryMillis = editor.expireEpochSeconds?.let { it * 1000L }
            val lastUpdated = editor.lastUpdatedMillis?.let { millis ->
                val date = Date(millis)
                "${DateFormat.getDateFormat(context).format(date)}, ${DateFormat.getTimeFormat(context).format(date)}"
            }
            val hasDetails = trafficValue != null || expiryMillis != null || editor.updateIntervalHours != null ||
                lastUpdated != null || editor.announce != null
            if (hasDetails) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(dimens.m),
                ) {
                    trafficValue?.let { EditorDetailRow(R.string.dashboard_subscription_detail_traffic, it) }
                    expiryMillis?.let { millis ->
                        EditorDetailRow(
                            R.string.dashboard_subscription_detail_expiry,
                            DateFormat.getDateFormat(context).format(Date(millis)),
                        )
                    }
                    editor.updateIntervalHours?.let { hours ->
                        EditorDetailRow(
                            R.string.dashboard_subscription_detail_refresh_interval,
                            stringResource(R.string.dashboard_subscription_detail_interval_value, hours),
                        )
                    }
                    lastUpdated?.let { EditorDetailRow(R.string.dashboard_subscription_detail_last_updated, it) }
                    editor.announce?.let { announce ->
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text(
                            text = announce,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            if (editor.supportUrl != null || editor.webPageUrl != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimens.s),
                ) {
                    editor.supportUrl?.let { url ->
                        OutlinedButton(
                            onClick = { openHttpsUrl(context, url) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = KaihiIcons.Outlined.SupportAgent,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(text = stringResource(R.string.dashboard_subscription_detail_support))
                        }
                    }
                    editor.webPageUrl?.let { url ->
                        OutlinedButton(
                            onClick = { openHttpsUrl(context, url) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = KaihiIcons.Outlined.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text(text = stringResource(R.string.dashboard_subscription_detail_web_page))
                        }
                    }
                }
            }

            KaihiTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                modifier = Modifier.fillMaxWidth(),
                label = UiText.Resource(R.string.dashboard_subscription_name_label),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done,
                ),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(text = stringResource(R.string.dashboard_subscription_delete))
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = { onRename(name) },
                    enabled = name.isNotBlank() && name.trim() != editor.name,
                ) {
                    Text(text = stringResource(R.string.dashboard_subscription_save))
                }
            }
        }
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = UiText.Resource(R.string.dashboard_subscription_delete_confirm_title),
            body = UiText.Resource(R.string.dashboard_subscription_delete_confirm_body),
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@Composable
private fun EditorDetailRow(labelRes: Int, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f).padding(start = MaterialTheme.dimens.l),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
}

private fun openHttpsUrl(context: Context, value: String) {
    val uri = Uri.parse(value)
    if (uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()) {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}
