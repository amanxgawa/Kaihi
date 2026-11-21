package app.kaihi.android.feature.importing.impl.ui.screen.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kaihi.android.R
import app.kaihi.android.feature.importing.impl.ui.model.AlreadyAddedUi
import app.kaihi.android.feature.importing.impl.ui.model.CountryGroupUi
import app.kaihi.android.feature.importing.impl.ui.model.ImportPreviewUi
import app.kaihi.android.platform.ui.icons.KaihiIcons
import app.kaihi.android.platform.ui.components.SubscriptionUsageCard as UsageSummary
import app.kaihi.android.platform.ui.theme.dimens
import app.kaihi.android.platform.ui.util.toFlagEmoji
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
    fillHeight: Boolean,
    modifier: Modifier = Modifier,
) {
    val dimens = MaterialTheme.dimens
    val context = LocalContext.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(dimens.m),
    ) {
        Text(
            text = when {
                alreadyAdded != null -> stringResource(R.string.import_preview_already_title, alreadyAdded.name)
                preview?.kind == ImportPreviewUi.Kind.SINGLE_SERVER -> stringResource(R.string.import_preview_title_single)
                preview?.kind == ImportPreviewUi.Kind.SERVERS -> stringResource(R.string.import_preview_title_servers)
                else -> stringResource(R.string.import_preview_title_subscription)
            },
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Column(
            modifier = Modifier.weight(1f, fill = fillHeight).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(dimens.m),
        ) {
            when {
                alreadyAdded != null -> Unit
                preview?.kind == ImportPreviewUi.Kind.SINGLE_SERVER -> SingleServerPreview(preview)
                preview?.kind == ImportPreviewUi.Kind.SUBSCRIPTION -> SubscriptionPreview(preview)
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
                        text = context.resources.getQuantityString(R.plurals.import_preview_skipped, preview.skippedCount, preview.skippedCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (preview.alreadyAddedCount > 0) {
                    Text(
                        text = context.resources.getQuantityString(R.plurals.import_preview_already_have, preview.alreadyAddedCount, preview.alreadyAddedCount),
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
        }

        Column(verticalArrangement = Arrangement.spacedBy(dimens.xs)) {
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
}

@Composable
private fun SingleServerPreview(preview: ImportPreviewUi) {
    val dimens = MaterialTheme.dimens
    Column(verticalArrangement = Arrangement.spacedBy(dimens.xs)) {
        val colors = MaterialTheme.colorScheme
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

@Composable
private fun SubscriptionPreview(preview: ImportPreviewUi) {
    val colors = MaterialTheme.colorScheme
    val dimens = MaterialTheme.dimens
    Column(verticalArrangement = Arrangement.spacedBy(dimens.xs)) {
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

        preview.usage?.let { usage ->
            UsageSummary(
                usage = usage,
                shape = RectangleShape,
                containerColor = Color.Transparent,
                contentPadding = PaddingValues(0.dp),
                modifier = Modifier.padding(top = dimens.s),
            )
        }
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
