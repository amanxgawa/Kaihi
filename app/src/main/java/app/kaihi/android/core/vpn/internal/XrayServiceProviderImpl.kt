package app.kaihi.android.core.vpn.internal

import app.kaihi.android.core.vpn.XrayControl
import app.kaihi.android.core.vpn.XrayMonitoring
import app.kaihi.android.core.vpn.XrayServiceProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class XrayServiceProviderImpl @Inject constructor(
    override val control: XrayControl,
    override val monitoring: XrayMonitoring,
) : XrayServiceProvider
