package app.kaihi.android.feature.dashboard.impl.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kaihi.android.R
import app.kaihi.android.feature.dashboard.impl.ui.mvi.ServerContract
import app.kaihi.android.feature.dashboard.impl.ui.mvi.ServerViewModel
import app.kaihi.android.feature.dashboard.impl.ui.mvi.isBusy
import app.kaihi.android.feature.dashboard.impl.ui.screen.components.ReconnectToApplyBanner
import app.kaihi.android.feature.dashboard.impl.ui.screen.components.ServerAdvancedSection
import app.kaihi.android.platform.ui.icons.KaihiIcons
import app.kaihi.android.platform.ui.theme.size.dimens
import app.kaihi.android.platform.ui.util.CollectAsEffect
import app.kaihi.android.platform.ui.util.asString

@Composable
internal fun ServerScreen(
    serverId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServerViewModel = hiltViewModel<ServerViewModel, ServerViewModel.Factory>(
        creationCallback = { factory -> factory.create(serverId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            snackbarHostState.showSnackbar(message.asString(context))
            viewModel.processIntent(ServerContract.Intent.ErrorDismissed)
        }
    }

    viewModel.effect.CollectAsEffect { effect ->
        when (effect) {
            ServerContract.Effect.NavigateBack -> onBack()
        }
    }

    val dimens = MaterialTheme.dimens
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = dimens.m, end = dimens.l),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.processIntent(ServerContract.Intent.BackRequested) }) {
                    Icon(
                        imageVector = KaihiIcons.Outlined.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text(
                    text = state.server?.name.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            state.server?.let { server ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(dimens.l),
                    verticalArrangement = Arrangement.spacedBy(dimens.l),
                ) {
                    Column {
                        AnimatedVisibility(
                            visible = state.showReconnectToApply,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            ReconnectToApplyBanner(
                                onReconnect = {
                                    viewModel.processIntent(ServerContract.Intent.ReconnectToApplyRequested)
                                },
                                enabled = !state.isBusy,
                                modifier = Modifier.padding(bottom = dimens.l),
                            )
                        }
                        ServerAdvancedSection(
                            fingerprint = server.fingerprint,
                            isXhttp = server.isXhttp,
                            muxEnabled = server.muxEnabled,
                            muxConcurrency = server.muxConcurrency,
                            fragmentEnabled = server.fragmentEnabled,
                            onFingerprintChanged = { value ->
                                viewModel.processIntent(ServerContract.Intent.FingerprintChanged(value))
                            },
                            onMuxEnabledChanged = { enabled ->
                                viewModel.processIntent(ServerContract.Intent.MuxEnabledChanged(enabled))
                            },
                            onFragmentEnabledChanged = { enabled ->
                                viewModel.processIntent(ServerContract.Intent.FragmentEnabledChanged(enabled))
                            },
                            onMuxConcurrencyChanged = { value ->
                                viewModel.processIntent(ServerContract.Intent.MuxConcurrencyChanged(value))
                            },
                        )
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(dimens.l),
        )
    }
}
