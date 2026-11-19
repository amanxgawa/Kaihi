package app.kaihi.android.feature.dashboard.impl.ui.mvi

import app.kaihi.android.core.vpn.XrayEngineState

internal val XrayEngineState.isBusy: Boolean
    get() = this == XrayEngineState.STARTING || this == XrayEngineState.STOPPING
