package app.kaihi.android.feature.importing.impl.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kaihi.android.R
import app.kaihi.android.feature.importing.impl.ui.mvi.ImportContract
import app.kaihi.android.feature.importing.impl.ui.mvi.ImportViewModel
import app.kaihi.android.feature.importing.impl.ui.screen.components.ImportChooseContent
import app.kaihi.android.feature.importing.impl.ui.screen.components.ImportPreviewContent
import app.kaihi.android.platform.ui.theme.dimens
import app.kaihi.android.platform.ui.theme.kaihiEffectsSpec
import app.kaihi.android.platform.ui.theme.kaihiSpatialSpec
import app.kaihi.android.platform.ui.util.CollectAsEffect
import app.kaihi.android.platform.ui.util.asString

private enum class ImportStep {
    CHOOSE,
    INPUT,
    CHECKING,
    PREVIEW,
}

@Composable
internal fun ImportSheet(
    initialText: String?,
    offerServerSetup: Boolean,
    onClose: () -> Unit,
    onSetUpServer: () -> Unit,
    onNavigateToDashboard: (firstAddedServerId: String) -> Unit,
    onShowExisting: (serverId: String?) -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        initialText?.let { viewModel.processIntent(ImportContract.Intent.Prefilled(it)) }
    }

    viewModel.effect.CollectAsEffect { effect ->
        when (effect) {
            is ImportContract.Effect.ImportSucceeded -> onNavigateToDashboard(effect.firstAddedServerId)
            is ImportContract.Effect.ShowExisting -> onShowExisting(effect.serverId)
            ImportContract.Effect.SetUpServer -> onSetUpServer()
        }
    }

    val step = when {
        state.isImporting -> ImportStep.CHECKING
        state.preview != null || state.alreadyAdded != null -> ImportStep.PREVIEW
        state.isManualEntry -> ImportStep.INPUT
        initialText != null && state.errorMessage == null -> ImportStep.CHECKING
        else -> ImportStep.CHOOSE
    }
    val dimens = MaterialTheme.dimens

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dimens.l)
            .padding(bottom = dimens.l),
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                (fadeIn(animationSpec = kaihiEffectsSpec()) togetherWith
                    fadeOut(animationSpec = kaihiEffectsSpec())) using
                    SizeTransform(clip = false) { _, _ -> kaihiSpatialSpec() }
            },
            label = "importStep",
        ) { targetStep ->
            when (targetStep) {
                ImportStep.CHOOSE -> ImportChooseContent(
                    offerServerSetup = offerServerSetup,
                    onPaste = { viewModel.processIntent(ImportContract.Intent.Pasted(it)) },
                    onEnterManually = {
                        viewModel.processIntent(ImportContract.Intent.ManualEntryRequested)
                    },
                    onSetUpServer = {
                        viewModel.processIntent(ImportContract.Intent.SetUpServerRequested)
                    },
                )
                ImportStep.INPUT -> ImportInputContent(
                    state = state,
                    onTextChanged = { viewModel.processIntent(ImportContract.Intent.TextChanged(it)) },
                    onImport = { viewModel.processIntent(ImportContract.Intent.ImportRequested) },
                )
                ImportStep.CHECKING -> ImportCheckingContent(
                    onCancel = {
                        if (initialText != null) onClose()
                        else viewModel.processIntent(ImportContract.Intent.CancelRequested)
                    },
                )
                ImportStep.PREVIEW -> ImportPreviewContent(
                    preview = state.preview,
                    alreadyAdded = state.alreadyAdded,
                    expandedGroups = state.expandedGroups,
                    isAdding = state.isAdding,
                    onAdd = { viewModel.processIntent(ImportContract.Intent.AddRequested) },
                    onDismiss = onClose,
                    onGroupToggled = { viewModel.processIntent(ImportContract.Intent.GroupToggled(it)) },
                    onShowExisting = { viewModel.processIntent(ImportContract.Intent.ShowExistingRequested) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ImportInputContent(
    state: ImportContract.State,
    onTextChanged: (String) -> Unit,
    onImport: () -> Unit,
) {
    val dimens = MaterialTheme.dimens

    Column(verticalArrangement = Arrangement.spacedBy(dimens.m)) {
        Text(
            text = stringResource(R.string.import_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
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
            minLines = 3,
            maxLines = 6,
        )
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

@Composable
private fun ImportCheckingContent(onCancel: () -> Unit) {
    val dimens = MaterialTheme.dimens

    Column(verticalArrangement = Arrangement.spacedBy(dimens.m)) {
        Text(
            text = stringResource(R.string.import_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.m),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(dimens.icon))
            Text(
                text = stringResource(R.string.import_progress),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.import_cancel))
        }
    }
}
