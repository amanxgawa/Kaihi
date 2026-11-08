package app.kaihi.android.feature.dashboard.impl.ui.mvi

import app.kaihi.android.core.vpn.XrayEngineState
import app.kaihi.android.core.vpn.XrayVpnProfile
import app.kaihi.android.core.vpn.configfactory.XrayClientConfigFactory

internal fun reconnectToApplyServerId(
    engineState: XrayEngineState,
    sessionServerId: String?,
    sessionSettingsKey: String?,
    sessionServer: XrayVpnProfile?,
    ipv6Enabled: Boolean,
): String? {
    if (engineState != XrayEngineState.RUNNING) return null
    val serverId = sessionServerId ?: return null
    val runningSettingsKey = sessionSettingsKey ?: return null
    val savedServer = sessionServer ?: return null

    return serverId.takeIf {
        XrayClientConfigFactory.settingsKey(savedServer, ipv6Enabled) != runningSettingsKey
    }
}
