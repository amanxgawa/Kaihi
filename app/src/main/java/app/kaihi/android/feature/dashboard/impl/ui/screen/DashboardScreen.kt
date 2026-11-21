package app.kaihi.android.feature.dashboard.impl.ui.screen

import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kaihi.android.R
import app.kaihi.android.feature.dashboard.impl.ui.mvi.DashboardContract
import app.kaihi.android.feature.dashboard.impl.ui.mvi.DashboardViewModel
import app.kaihi.android.feature.dashboard.impl.ui.mvi.isBusy
import app.kaihi.android.feature.dashboard.impl.ui.screen.components.ConnectFab
import app.kaihi.android.feature.dashboard.impl.ui.screen.components.ConnectionHeader
import app.kaihi.android.feature.dashboard.impl.ui.screen.components.ReconnectToApplyBanner
import app.kaihi.android.feature.dashboard.impl.ui.screen.components.ServerDetailsSheet
import app.kaihi.android.feature.dashboard.impl.ui.screen.components.SubscriptionEditorSheet
import app.kaihi.android.feature.dashboard.impl.ui.screen.components.connectFabClearance
import app.kaihi.android.feature.dashboard.impl.ui.screen.components.rememberIsConnectionToggleScrolledAway
import app.kaihi.android.feature.dashboard.impl.ui.screen.components.subscriptionItems
import app.kaihi.android.feature.dashboard.impl.ui.model.SubscriptionKind
import app.kaihi.android.platform.ui.components.KaihiMark
import app.kaihi.android.platform.ui.components.AddServerSheet
import app.kaihi.android.platform.ui.components.ConfirmDialog
import app.kaihi.android.platform.ui.icons.KaihiIcons
import app.kaihi.android.platform.ui.theme.dimens
import app.kaihi.android.platform.ui.util.CollectAsEffect
import app.kaihi.android.platform.ui.util.asString
import app.kaihi.android.platform.ui.util.UiText

private const val CONNECTION_ITEM_KEY = "connection"
private const val RECONNECT_TO_APPLY_ITEM_KEY = "reconnect-to-apply"
private const val DEPLOY_ITEM_KEY = "deploy"

private enum class AddSheetMode { WITH_SETUP, WITHOUT_SETUP }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DashboardScreen(
    selectServerId: String?,
    onSettingsClick: () -> Unit,
    onNavigateToWizard: (subscriptionId: String?) -> Unit,
    onNavigateToImport: (text: String?) -> Unit,
    onNavigateToServer: (serverId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = hiltViewModel<DashboardViewModel, DashboardViewModel.Factory>(
        creationCallback = { factory -> factory.create(selectServerId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val isToggleScrolledAway by rememberIsConnectionToggleScrolledAway(listState)
    var addSheetMode by rememberSaveable { mutableStateOf<AddSheetMode?>(null) }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message.asString(context))
            viewModel.processIntent(DashboardContract.Intent.ErrorDismissed)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val intent = if (result.resultCode == android.app.Activity.RESULT_OK) {
            DashboardContract.Intent.VpnPermissionGranted
        } else {
            DashboardContract.Intent.VpnPermissionDenied
        }
        viewModel.processIntent(intent)
    }

    viewModel.effect.CollectAsEffect { effect ->
        when (effect) {
            is DashboardContract.Effect.RequestVpnPermission -> {
                VpnService.prepare(context)?.let(permissionLauncher::launch)
                    ?: viewModel.processIntent(DashboardContract.Intent.VpnPermissionGranted)
            }

            is DashboardContract.Effect.ShareText -> {
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, effect.text)
                }
                context.startActivity(Intent.createChooser(sendIntent, null))
            }

            is DashboardContract.Effect.NavigateToWizard -> onNavigateToWizard(effect.subscriptionId)
            is DashboardContract.Effect.NavigateToServer -> onNavigateToServer(effect.serverId)
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    KaihiMark(
                        modifier = Modifier.size(MaterialTheme.dimens.xl),
                        contentDescription = stringResource(R.string.app_name),
                    )
                },
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = KaihiIcons.Outlined.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = isToggleScrolledAway && state.subscriptions.isNotEmpty(),
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                ConnectFab(
                    state = state,
                    onClick = { viewModel.processIntent(DashboardContract.Intent.ToggleConnection) },
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        DashboardContent(
            state = state,
            listState = listState,
            innerPadding = innerPadding,
            onIntent = viewModel::processIntent,
            onShowAddSheet = { withSetup ->
                addSheetMode = if (withSetup) AddSheetMode.WITH_SETUP else AddSheetMode.WITHOUT_SETUP
            },
        )
    }

    state.serverDetails?.let { details ->
        ServerDetailsSheet(
            details = details,
            isAdvancedMode = state.isAdvancedMode,
            onDismiss = { viewModel.processIntent(DashboardContract.Intent.ServerDetailsDismissed) },
            onShare = { viewModel.processIntent(DashboardContract.Intent.ShareServerRequested(details.id)) },
            onManageServer = { viewModel.processIntent(DashboardContract.Intent.ServerManageRequested(details.id)) },
            onForget = { viewModel.processIntent(DashboardContract.Intent.ServerForgotten(details.id)) },
        )
    }

    state.subscriptionEditor?.let { editor ->
        SubscriptionEditorSheet(
            editor = editor,
            onDismiss = { viewModel.processIntent(DashboardContract.Intent.SubscriptionEditorDismissed) },
            onRename = { name -> viewModel.processIntent(DashboardContract.Intent.SubscriptionRenamed(editor.id, name)) },
            onDelete = { viewModel.processIntent(DashboardContract.Intent.SubscriptionDeleted(editor.id)) },
        )
    }

    if (state.remoteShareConfirmationSubscriptionId != null) {
        ConfirmDialog(
            title = UiText.Resource(R.string.dashboard_subscription_share_link_confirm_title),
            body = UiText.Resource(R.string.dashboard_subscription_share_link_confirm_body),
            confirmText = UiText.Resource(R.string.dashboard_subscription_share_link_confirm_action),
            onConfirm = { viewModel.processIntent(DashboardContract.Intent.RemoteSubscriptionShareConfirmed) },
            onDismiss = { viewModel.processIntent(DashboardContract.Intent.RemoteSubscriptionShareDismissed) },
        )
    }

    addSheetMode?.let { mode ->
        AddServerSheet(
            onDismiss = { addSheetMode = null },
            onPaste = { text ->
                addSheetMode = null
                onNavigateToImport(text)
            },
            onEnterManually = {
                addSheetMode = null
                onNavigateToImport(null)
            },
            onSetUpServer = if (mode == AddSheetMode.WITH_SETUP) {
                {
                    addSheetMode = null
                    viewModel.processIntent(DashboardContract.Intent.DeployServerRequested(subscriptionId = null))
                }
            } else null,
        )
    }
}

@Composable
private fun DashboardContent(
    state: DashboardContract.State,
    listState: LazyListState,
    innerPadding: PaddingValues,
    onIntent: (DashboardContract.Intent) -> Unit,
    onShowAddSheet: (withSetup: Boolean) -> Unit,
) {
    val dimens = MaterialTheme.dimens
    val layoutDirection = LocalLayoutDirection.current

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = innerPadding.calculateStartPadding(layoutDirection) + dimens.l,
            end = innerPadding.calculateEndPadding(layoutDirection) + dimens.l,
            top = innerPadding.calculateTopPadding(),
            bottom = innerPadding.calculateBottomPadding() + connectFabClearance,
        ),
    ) {
        item(key = CONNECTION_ITEM_KEY) {
            ConnectionHeader(
                state = state,
                onToggleConnection = { onIntent(DashboardContract.Intent.ToggleConnection) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = dimens.xl),
            )
        }

        if (state.reconnectToApplyServerId != null) {
            item(key = RECONNECT_TO_APPLY_ITEM_KEY) {
                ReconnectToApplyBanner(
                    onReconnect = { onIntent(DashboardContract.Intent.ReconnectToApplyRequested) },
                    enabled = !state.engineState.isBusy,
                    modifier = Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .padding(bottom = dimens.l),
                )
            }
        }

        subscriptionItems(
            subscriptions = state.subscriptions,
            onToggleExpanded = { id -> onIntent(DashboardContract.Intent.SubscriptionExpandToggled(id)) },
            onAddServer = { subscription ->
                if (subscription.kind == SubscriptionKind.IMPORTED) {
                    onShowAddSheet(false)
                } else {
                    onIntent(DashboardContract.Intent.DeployServerRequested(subscription.id))
                }
            },
            onShare = { id -> onIntent(DashboardContract.Intent.ShareSubscriptionRequested(id)) },
            onEdit = { id -> onIntent(DashboardContract.Intent.SubscriptionEditorRequested(id)) },
            onRefresh = { id -> onIntent(DashboardContract.Intent.RefreshSubscriptionRequested(id)) },
            onSelectServer = { id -> onIntent(DashboardContract.Intent.ServerSelected(id)) },
            onServerDetails = { id -> onIntent(DashboardContract.Intent.ServerDetailsRequested(id)) },
        )

        if (state.isLibraryLoaded) {
            item(key = DEPLOY_ITEM_KEY) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = dimens.l),
                ) {
                    if (state.subscriptions.isEmpty()) {
                        Text(
                            text = stringResource(R.string.dashboard_empty_hint),
                            modifier = Modifier.fillMaxWidth().padding(bottom = dimens.l),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Button(
                        onClick = { onShowAddSheet(true) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(imageVector = KaihiIcons.Outlined.Add, contentDescription = null)
                        Text(
                            text = stringResource(R.string.dashboard_add_server),
                            modifier = Modifier.padding(start = dimens.s),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}
