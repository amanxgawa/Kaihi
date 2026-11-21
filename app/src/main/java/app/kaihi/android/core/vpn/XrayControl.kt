package app.kaihi.android.core.vpn

import app.kaihi.android.core.vpn.configfactory.XrayClientConfigFactory

interface XrayControl {
    fun needsVpnPermission(): Boolean
    suspend fun start(config: XrayRuntimeConfig): Result<Unit>
    suspend fun stop(): Result<Unit>
}

/** Starts a session on [profile] with [settings], the user's current tunnel settings. */
suspend fun XrayControl.start(profile: XrayVpnProfile, settings: TunnelSettings): Result<Unit> =
    start(XrayClientConfigFactory.createRuntimeConfig(profile, settings))
