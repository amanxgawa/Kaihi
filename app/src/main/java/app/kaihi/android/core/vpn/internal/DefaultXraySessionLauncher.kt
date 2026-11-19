package app.kaihi.android.core.vpn.internal

import app.kaihi.android.core.vpn.TunnelSettingsSource
import app.kaihi.android.core.vpn.XrayControl
import app.kaihi.android.core.vpn.XraySessionLauncher
import app.kaihi.android.core.vpn.XrayVpnProfile
import app.kaihi.android.core.vpn.configfactory.XrayClientConfigFactory
import javax.inject.Inject

internal class DefaultXraySessionLauncher @Inject constructor(
    private val control: XrayControl,
    private val tunnelSettingsSource: TunnelSettingsSource,
) : XraySessionLauncher {
    override suspend fun start(profile: XrayVpnProfile): Result<Unit> =
        control.start(XrayClientConfigFactory.createRuntimeConfig(profile, tunnelSettingsSource.current()))
}
