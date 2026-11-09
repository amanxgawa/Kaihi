package app.kaihi.android.core.vpn.internal

import app.kaihi.android.core.vpn.TunnelSettings
import app.kaihi.android.core.vpn.TunnelSettingsSource
import app.kaihi.android.core.vpn.VpnPreferences
import app.kaihi.android.platform.mode.AppMode
import app.kaihi.android.platform.mode.AppModeRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Singleton
internal class RepositoryTunnelSettingsSource @Inject constructor(
    private val vpnPreferences: VpnPreferences,
    private val appModeRepository: AppModeRepository,
) : TunnelSettingsSource {

    override val settings: Flow<TunnelSettings> = combine(
        appModeRepository.mode,
        vpnPreferences.ipv6Enabled,
        ::tunnelSettings,
    )

    override fun current(): TunnelSettings = tunnelSettings(
        appModeRepository.mode.value,
        vpnPreferences.ipv6Enabled.value,
    )

    private fun tunnelSettings(mode: AppMode, ipv6Enabled: Boolean) = TunnelSettings(
        advancedMode = mode == AppMode.ADVANCED,
        ipv6Enabled = ipv6Enabled,
    )
}
