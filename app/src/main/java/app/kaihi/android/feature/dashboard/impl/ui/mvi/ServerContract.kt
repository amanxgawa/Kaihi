package app.kaihi.android.feature.dashboard.impl.ui.mvi

import app.kaihi.android.core.vpn.XrayEngineState
import app.kaihi.android.core.vpn.spec.XrayFingerprintSetting
import app.kaihi.android.feature.dashboard.impl.ui.model.ServerSettingsUi
import app.kaihi.android.platform.ui.util.UiText

internal object ServerContract {

    data class State(
        val server: ServerSettingsUi? = null,
        val engineState: XrayEngineState = XrayEngineState.IDLE,
        val showReconnectToApply: Boolean = false,
        val errorMessage: UiText? = null,
    )

    sealed interface Intent {
        data object BackRequested : Intent
        data object ReconnectToApplyRequested : Intent
        data object ErrorDismissed : Intent
        data class FingerprintChanged(val value: XrayFingerprintSetting) : Intent
        data class MuxEnabledChanged(val enabled: Boolean) : Intent
        data class FragmentEnabledChanged(val enabled: Boolean) : Intent
        data class MuxConcurrencyChanged(val value: Int) : Intent
    }

    sealed interface Effect {
        data object NavigateBack : Effect
    }
}

internal val ServerContract.State.isBusy: Boolean
    get() = engineState == XrayEngineState.STARTING || engineState == XrayEngineState.STOPPING
