package app.kaihi.android.feature.dashboard.impl.ui.mvi

import androidx.compose.runtime.Stable
import app.kaihi.android.R
import app.kaihi.android.core.vpn.XrayEngineState
import app.kaihi.android.core.vpn.XrayServiceProvider
import app.kaihi.android.core.vpn.findServer
import app.kaihi.android.feature.dashboard.impl.domain.PendingSettingsMonitor
import app.kaihi.android.feature.dashboard.impl.domain.ServerLibraryCoordinator
import app.kaihi.android.platform.ui.mvi.MviViewModel
import app.kaihi.android.platform.ui.util.UiText
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber

@Stable
@HiltViewModel(assistedFactory = ServerViewModel.Factory::class)
internal class ServerViewModel @AssistedInject constructor(
    @Assisted private val serverId: String,
    private val coordinator: ServerLibraryCoordinator,
    private val pendingSettingsMonitor: PendingSettingsMonitor,
    private val serviceProvider: XrayServiceProvider,
) : MviViewModel<ServerContract.State, ServerContract.Intent, ServerContract.Effect>(ServerContract.State()) {

    private var didNavigateBack = false

    init {
        coordinator.library
            .onEach { library ->
                val server = library.subscriptions.findServer(serverId)?.toSettingsUi()
                reduce { copy(server = server) }
                if (server == null && !didNavigateBack) {
                    didNavigateBack = true
                    sendEffect(ServerContract.Effect.NavigateBack)
                }
            }
            .launchIn(scope)
        pendingSettingsMonitor.pendingServerId
            .onEach { pendingServerId ->
                reduce { copy(showReconnectToApply = pendingServerId == serverId) }
            }
            .launchIn(scope)
        serviceProvider.monitoring.state
            .onEach { engineState -> reduce { copy(engineState = engineState) } }
            .launchIn(scope)
    }

    override suspend fun onIntent(intent: ServerContract.Intent) {
        when (intent) {
            ServerContract.Intent.BackRequested -> sendEffect(ServerContract.Effect.NavigateBack)
            ServerContract.Intent.ErrorDismissed -> reduce { copy(errorMessage = null) }
            ServerContract.Intent.ReconnectToApplyRequested -> reconnectToApply()
            is ServerContract.Intent.FingerprintChanged -> coordinator.setFingerprint(serverId, intent.value)
            is ServerContract.Intent.MuxEnabledChanged -> coordinator.setMuxEnabled(serverId, intent.enabled)
            is ServerContract.Intent.FragmentEnabledChanged -> coordinator.setFragmentEnabled(serverId, intent.enabled)
            is ServerContract.Intent.MuxConcurrencyChanged -> coordinator.setMuxConcurrency(serverId, intent.value)
        }
    }

    private suspend fun reconnectToApply() {
        if (state.value.isBusy || state.value.engineState != XrayEngineState.RUNNING) return
        if (!state.value.showReconnectToApply) return
        reduce { copy(engineState = XrayEngineState.STARTING, errorMessage = null) }
        pendingSettingsMonitor.restart(serverId).onFailure { error ->
            Timber.e(error, "start() dispatch failed")
            reduce {
                copy(
                    engineState = XrayEngineState.RUNNING,
                    errorMessage = UiText.Resource(R.string.dashboard_error_failed_to_start_vpn),
                )
            }
        }
    }

    @AssistedFactory
    interface Factory {
        fun create(serverId: String): ServerViewModel
    }
}
