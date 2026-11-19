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
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kaihi.android.R
import app.kaihi.android.feature.importing.impl.ui.mvi.ImportContract
import app.kaihi.android.feature.importing.impl.ui.mvi.ImportViewModel
import app.kaihi.android.feature.importing.impl.ui.screen.components.ImportPreviewContent
import app.kaihi.android.platform.ui.icons.KaihiIcons
import app.kaihi.android.platform.ui.theme.dimens
import app.kaihi.android.platform.ui.util.CollectAsEffect
import app.kaihi.android.platform.ui.util.asString

@Composable
internal fun ImportScreen(
    initialText: String?,
    onBack: () -> Unit,
    onNavigateToDashboard: (firstAddedServerId: String) -> Unit,
    onShowExisting: (serverId: String?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        initialText?.let { viewModel.processIntent(ImportContract.Intent.Prefilled(it)) }
    }

    viewModel.effect.CollectAsEffect { effect ->
        when (effect) {
            ImportContract.Effect.NavigateBack -> onBack()
            is ImportContract.Effect.ImportSucceeded -> onNavigateToDashboard(effect.firstAddedServerId)
            is ImportContract.Effect.ShowExisting -> onShowExisting(effect.serverId)
        }
    }

    val isPreviewShown = state.preview != null || state.alreadyAdded != null
    BackHandler(enabled = isPreviewShown) {
        viewModel.processIntent(ImportContract.Intent.BackRequested)
    }

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
    ) { innerPadding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = MaterialTheme.dimens.l)
        if (isPreviewShown) {
            ImportPreviewContent(
                preview = state.preview,
                alreadyAdded = state.alreadyAdded,
                expandedGroups = state.expandedGroups,
                isAdding = state.isAdding,
                onAdd = { viewModel.processIntent(ImportContract.Intent.AddRequested) },
                onDismiss = { viewModel.processIntent(ImportContract.Intent.PreviewDismissed) },
                onGroupToggled = { viewModel.processIntent(ImportContract.Intent.GroupToggled(it)) },
                onShowExisting = { viewModel.processIntent(ImportContract.Intent.ShowExistingRequested) },
                modifier = contentModifier.verticalScroll(rememberScrollState()),
            )
        } else {
            ImportContent(
                state = state,
                onTextChanged = { viewModel.processIntent(ImportContract.Intent.TextChanged(it)) },
                onImport = { viewModel.processIntent(ImportContract.Intent.ImportRequested) },
                onCancel = { viewModel.processIntent(ImportContract.Intent.CancelRequested) },
                modifier = contentModifier,
            )
        }
    }
}

@Composable
private fun ImportContent(
    state: ImportContract.State,
    onTextChanged: (String) -> Unit,
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
            enabled = !state.isImporting && !state.isAdding,
            label = { Text(stringResource(R.string.import_input_label)) },
            minLines = 5,
            maxLines = 10,
        )

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
            enabled = state.text.isNotBlank() && !state.isImporting && !state.isAdding,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.import_action))
        }
    }
}
