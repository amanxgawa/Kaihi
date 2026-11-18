package app.kaihi.android.platform.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.kaihi.android.R
import app.kaihi.android.platform.ui.icons.KaihiIcons
import app.kaihi.android.platform.ui.theme.KaihiTheme
import app.kaihi.android.platform.ui.theme.size.dimens
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AddServerSheet(
    onDismiss: () -> Unit,
    onPaste: (text: String) -> Unit,
    onEnterManually: () -> Unit,
    onSetUpServer: (() -> Unit)?,
) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showEmptyPasteError by rememberSaveable { mutableStateOf(false) }
    val dimens = MaterialTheme.dimens

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.l)
                .padding(bottom = dimens.l),
            verticalArrangement = Arrangement.spacedBy(dimens.xs),
        ) {
            Text(
                text = stringResource(R.string.add_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = dimens.xs),
            )
            AddServerRow(
                icon = KaihiIcons.Outlined.Cable,
                title = stringResource(R.string.add_sheet_paste_title),
                description = stringResource(R.string.add_sheet_paste_description),
                onClick = {
                    coroutineScope.launch {
                        val clipData = clipboard.getClipEntry()?.clipData
                        val text = if (clipData != null && clipData.itemCount > 0) {
                            clipData.getItemAt(0).coerceToText(context)?.toString().orEmpty().trim()
                        } else {
                            ""
                        }
                        if (text.isNotBlank()) {
                            showEmptyPasteError = false
                            onPaste(text)
                        } else {
                            showEmptyPasteError = true
                        }
                    }
                },
            )
            if (showEmptyPasteError) {
                Text(
                    text = stringResource(R.string.add_sheet_paste_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = KaihiTheme.status.warning,
                    modifier = Modifier.padding(start = dimens.xl + dimens.m, bottom = dimens.xs),
                )
            }
            AddServerRow(
                icon = KaihiIcons.Outlined.Edit,
                title = stringResource(R.string.add_sheet_manual_title),
                description = stringResource(R.string.add_sheet_manual_description),
                onClick = onEnterManually,
            )
            if (onSetUpServer != null) {
                AddServerRow(
                    icon = KaihiIcons.Outlined.Dns,
                    title = stringResource(R.string.add_sheet_setup_title),
                    description = stringResource(R.string.add_sheet_setup_description),
                    onClick = onSetUpServer,
                )
            }
        }
    }
}

@Composable
private fun AddServerRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = MaterialTheme.dimens.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.dimens.m),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.dimens.xs),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
