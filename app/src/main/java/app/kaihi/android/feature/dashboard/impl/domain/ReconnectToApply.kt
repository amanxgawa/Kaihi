package app.kaihi.android.feature.dashboard.impl.domain

import app.kaihi.android.core.vpn.TunnelSettings
import app.kaihi.android.core.vpn.XrayEngineState
import app.kaihi.android.core.vpn.XrayVpnProfile
import app.kaihi.android.core.vpn.configfactory.XrayClientConfigFactory

internal fun reconnectToApplyServerId(
    engineState: XrayEngineState,
    sessionServerId: String?,
    sessionSettingsKey: String?,
    sessionServer: XrayVpnProfile?,
    settings: TunnelSettings,
): String? {
    if (engineState != XrayEngineState.RUNNING) return null
    val serverId = sessionServerId ?: return null
    val runningSettingsKey = sessionSettingsKey ?: return null
    val savedServer = sessionServer ?: return null

    return serverId.takeIf {
        XrayClientConfigFactory.settingsKey(savedServer, settings) != runningSettingsKey
    }
}
