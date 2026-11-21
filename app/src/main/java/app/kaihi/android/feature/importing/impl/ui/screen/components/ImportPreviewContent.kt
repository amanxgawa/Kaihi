package app.kaihi.android.feature.importing.impl.ui.screen.components

import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kaihi.android.R
import app.kaihi.android.feature.importing.impl.ui.model.AlreadyAddedUi
import app.kaihi.android.feature.importing.impl.ui.model.CountryGroupUi
import app.kaihi.android.feature.importing.impl.ui.model.ImportPreviewUi
import app.kaihi.android.feature.importing.impl.ui.model.PreviewLevel
import app.kaihi.android.platform.ui.icons.KaihiIcons
import app.kaihi.android.platform.ui.theme.KaihiTheme
import app.kaihi.android.platform.ui.theme.dimens
import app.kaihi.android.platform.ui.util.toFlagEmoji
import java.util.Date
import java.util.IllformedLocaleException
import java.util.Locale

@Composable
internal fun ImportPreviewContent(
    preview: ImportPreviewUi?,
    alreadyAdded: AlreadyAddedUi?,
    expandedGroups: Set<String>,
    isAdding: Boolean,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
    onGroupToggled: (String) -> Unit,
    onShowExisting: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = MaterialTheme.dimens
    val context = LocalContext.current

    Column(
        modifier = modifier.padding(top = dimens.l, bottom = dimens.l),
        verticalArrangement = Arrangement.spacedBy(dimens.m),
    ) {
        Text(
            text = when {
                alreadyAdded != null -> stringResource(R.string.import_preview_already_title, alreadyAdded.name)
                preview?.kind == ImportPreviewUi.Kind.SINGLE_SERVER -> stringResource(R.string.import_preview_title_single)
                preview?.kind == ImportPreviewUi.Kind.SERVERS -> stringResource(R.string.import_preview_title_servers)
                else -> stringResource(R.string.import_preview_title_subscription)
            },
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )

        when {
            alreadyAdded != null -> Unit
            preview?.kind == ImportPreviewUi.Kind.SINGLE_SERVER -> SingleServerCard(preview)
            preview?.kind == ImportPreviewUi.Kind.SUBSCRIPTION -> SubscriptionCard(preview)
        }

        if (preview != null && preview.kind != ImportPreviewUi.Kind.SINGLE_SERVER) {
            Text(
                text = stringResource(R.string.import_preview_servers_heading, preview.serverCount),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            preview.groups.forEach { group ->
                CountryGroup(
                    group = group,
                    expanded = group.key() in expandedGroups,
                    onToggle = { onGroupToggled(group.key()) },
                )
            }
        }

        if (preview != null) {
            if (preview.skippedCount > 0) {
                Text(
                    text = context.resources.getQuantityString(
                        R.plurals.import_preview_skipped,
                        preview.skippedCount,
                        preview.skippedCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (preview.alreadyAddedCount > 0) {
                Text(
                    text = context.resources.getQuantityString(
                        R.plurals.import_preview_already_have,
                        preview.alreadyAddedCount,
                        preview.alreadyAddedCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (preview.truncated) {
                Text(
                    text = stringResource(R.string.import_preview_truncated),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (alreadyAdded != null) {
            Button(onClick = onShowExisting, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.import_preview_show_existing))
            }
        } else {
            Button(
                onClick = onAdd,
                enabled = !isAdding,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (isAdding) {
                        stringResource(R.string.import_preview_adding)
                    } else if (preview?.kind == ImportPreviewUi.Kind.SUBSCRIPTION) {
                        stringResource(R.string.import_preview_add_subscription)
                    } else {
                        stringResource(R.string.import_preview_add)
                    },
                )
            }
        }

        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.import_cancel))
        }
    }
}

@Composable
private fun SingleServerCard(preview: ImportPreviewUi) {
    val colors = MaterialTheme.colorScheme
    val dimens = MaterialTheme.dimens
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(dimens.l),
            verticalArrangement = Arrangement.spacedBy(dimens.xs),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.s),
            ) {
                preview.singleServerCountryCode?.toFlagEmoji()?.let { flag ->
                    Text(flag, style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    text = preview.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
            }
            preview.singleServerCountryCode?.let { code ->
                Text(
                    text = countryName(code),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SubscriptionCard(preview: ImportPreviewUi) {
    val colors = MaterialTheme.colorScheme
    val dimens = MaterialTheme.dimens
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier.padding(dimens.l),
            verticalArrangement = Arrangement.spacedBy(dimens.m),
        ) {
            Text(
                text = preview.title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
            )
            preview.domain?.let { domain ->
                Text(
                    text = stringResource(R.string.import_preview_from, domain),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }

            if (preview.totalBytes != null && preview.totalBytes > 0L) {
                val used = preview.usedBytes ?: 0L
                val progressColor = when (preview.trafficLevel) {
                    PreviewLevel.NORMAL -> colors.primary
                    PreviewLevel.WARNING -> KaihiTheme.status.warning
                    PreviewLevel.ERROR -> KaihiTheme.status.error
                }
                LinearProgressIndicator(
                    progress = { (used.toDouble() / preview.totalBytes.toDouble()).toFloat().coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                    color = progressColor,
                    trackColor = colors.surfaceVariant,
                )
                if (preview.trafficLevel == PreviewLevel.ERROR) {
                    StatusLine(
                        text = stringResource(R.string.import_preview_traffic_used_up),
                        level = PreviewLevel.ERROR,
                    )
                } else {
                    Text(
                        text = stringResource(
                            R.string.import_preview_traffic,
                            Formatter.formatShortFileSize(context, used),
                            Formatter.formatShortFileSize(context, preview.totalBytes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = when (preview.trafficLevel) {
                            PreviewLevel.NORMAL -> colors.onSurface
                            PreviewLevel.WARNING -> KaihiTheme.status.warning
                            PreviewLevel.ERROR -> KaihiTheme.status.error
                        },
                    )
                }
            }

            preview.expiresAtMillis?.let { expiresAtMillis ->
                val date = DateFormat.getDateFormat(context).format(Date(expiresAtMillis))
                val expiryText = when (preview.expiryLevel) {
                    PreviewLevel.NORMAL -> stringResource(R.string.import_preview_works_until, date)
                    PreviewLevel.WARNING -> stringResource(R.string.import_preview_expires_soon, date)
                    PreviewLevel.ERROR -> stringResource(R.string.import_preview_expired, date)
                }
                StatusLine(text = expiryText, level = preview.expiryLevel)
            }

            preview.announce?.takeIf(String::isNotEmpty)?.let { announce ->
                Text(
                    text = announce,
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawLine(
                                color = colors.outline,
                                start = Offset(0f, 0f),
                                end = Offset(0f, size.height),
                                strokeWidth = 2.dp.toPx(),
                            )
                        }
                        .padding(start = dimens.m, top = dimens.xs, bottom = dimens.xs),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                )
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, level: PreviewLevel) {
    val color = when (level) {
        PreviewLevel.NORMAL -> MaterialTheme.colorScheme.onSurface
        PreviewLevel.WARNING -> KaihiTheme.status.warning
        PreviewLevel.ERROR -> KaihiTheme.status.error
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.dimens.xs),
    ) {
        if (level == PreviewLevel.ERROR) {
            Icon(
                imageVector = KaihiIcons.Outlined.Info,
                contentDescription = null,
                modifier = Modifier.size(MaterialTheme.dimens.icon),
                tint = color,
            )
        }
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}

@Composable
private fun CountryGroup(group: CountryGroupUi, expanded: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dimens = MaterialTheme.dimens
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = dimens.s),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.s),
        ) {
            group.countryCode?.toFlagEmoji()?.let { flag ->
                Text(flag, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                text = group.countryCode?.let(::countryName)
                    ?: stringResource(R.string.import_preview_unknown_location),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
            )
            Text(
                text = group.serverNames.size.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Icon(
                imageVector = KaihiIcons.Outlined.ExpandMore,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
            )
        }
        if (expanded) {
            Column(
                modifier = Modifier.padding(start = dimens.xl, bottom = dimens.s),
                verticalArrangement = Arrangement.spacedBy(dimens.xs),
            ) {
                group.serverNames.forEach { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun CountryGroupUi.key(): String = countryCode.orEmpty()

private fun countryName(code: String): String = try {
    Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.getDefault()).ifBlank { code }
} catch (_: IllformedLocaleException) {
    code
}
