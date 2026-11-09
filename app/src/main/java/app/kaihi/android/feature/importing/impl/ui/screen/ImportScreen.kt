package app.kaihi.android.feature.importing.impl.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kaihi.android.R
import app.kaihi.android.feature.importing.impl.domain.ImportCounts
import app.kaihi.android.feature.importing.impl.ui.mvi.ImportContract
import app.kaihi.android.feature.importing.impl.ui.mvi.ImportViewModel
import app.kaihi.android.platform.ui.icons.KaihiIcons
import app.kaihi.android.platform.ui.theme.size.dimens
import app.kaihi.android.platform.ui.util.CollectAsEffect
import app.kaihi.android.platform.ui.util.asString
import kotlinx.coroutines.launch

@Composable
internal fun ImportScreen(
    onBack: () -> Unit,
    onNavigateToDashboard: (firstAddedServerId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    viewModel.effect.CollectAsEffect { effect ->
        when (effect) {
            ImportContract.Effect.NavigateBack -> onBack()
            is ImportContract.Effect.ImportSucceeded -> coroutineScope.launch {
                snackbarHostState.showSnackbar(
                    message = importSummary(context, effect.counts),
                    duration = SnackbarDuration.Short,
                )
                onNavigateToDashboard(effect.firstAddedServerId)
            }
        }
    }

    val clipboard = LocalClipboard.current
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
                    .padding(start = MaterialTheme.dimens.m, end = MaterialTheme.dimens.l),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.processIntent(ImportContract.Intent.BackRequested) }) {
                    Icon(
                        imageVector = KaihiIcons.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.import_back),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = stringResource(R.string.import_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        ImportContent(
            state = state,
            onTextChanged = { viewModel.processIntent(ImportContract.Intent.TextChanged(it)) },
            onPaste = {
                coroutineScope.launch {
                    val clipEntry = clipboard.getClipEntry()
                    val clipData = clipEntry?.clipData
                    val text = if (clipData != null && clipData.itemCount > 0) {
                        clipData.getItemAt(0).coerceToText(context)?.toString().orEmpty()
                    } else {
                        ""
                    }
                    viewModel.processIntent(ImportContract.Intent.TextChanged(text))
                }
            },
            onImport = { viewModel.processIntent(ImportContract.Intent.ImportRequested) },
            onCancel = { viewModel.processIntent(ImportContract.Intent.CancelRequested) },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = MaterialTheme.dimens.l),
        )
    }
}

@Composable
private fun ImportContent(
    state: ImportContract.State,
    onTextChanged: (String) -> Unit,
    onPaste: () -> Unit,
    onImport: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = MaterialTheme.dimens

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(top = dimens.l, bottom = dimens.l),
        verticalArrangement = Arrangement.spacedBy(dimens.m),
    ) {
        Text(
            text = stringResource(R.string.import_helper),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = state.text,
            onValueChange = onTextChanged,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isImporting,
            label = { Text(stringResource(R.string.import_input_label)) },
            minLines = 5,
            maxLines = 10,
        )

        TextButton(
            onClick = onPaste,
            enabled = !state.isImporting,
            modifier = Modifier.align(Alignment.Start),
        ) {
            Text(stringResource(R.string.import_paste_from_clipboard))
        }

        if (state.isImporting) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.m),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    text = stringResource(R.string.import_progress),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onCancel) {
                    Text(stringResource(R.string.import_cancel))
                }
            }
        }

        state.errorMessage?.let { error ->
            Text(
                text = error.asString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Button(
            onClick = onImport,
            enabled = state.text.isNotBlank() && !state.isImporting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.import_action))
        }
    }
}

private fun importSummary(context: android.content.Context, counts: ImportCounts): String = buildList {
    add(context.resources.getQuantityString(R.plurals.import_summary_added, counts.added, counts.added))
    if (counts.duplicates > 0) {
        add(context.resources.getQuantityString(R.plurals.import_summary_duplicates, counts.duplicates, counts.duplicates))
    }
    if (counts.unsupported > 0) {
        add(context.resources.getQuantityString(R.plurals.import_summary_unsupported, counts.unsupported, counts.unsupported))
    }
    if (counts.invalid > 0) {
        add(context.resources.getQuantityString(R.plurals.import_summary_invalid, counts.invalid, counts.invalid))
    }
    if (counts.truncated) add(context.getString(R.string.import_summary_truncated))
}.joinToString(context.getString(R.string.import_summary_separator))
