package app.kaihi.android.core.vpn

import app.kaihi.android.core.mode.AppMode
import app.kaihi.android.core.mode.AppModeRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Singleton
class TunnelSettingsSource @Inject constructor(
    private val vpnPreferences: VpnPreferences,
    private val appModeRepository: AppModeRepository,
) {

    val settings: Flow<TunnelSettings> = combine(
        appModeRepository.mode,
        vpnPreferences.ipv6Enabled,
        vpnPreferences.excludedPackages,
        ::tunnelSettings,
    )

    fun current(): TunnelSettings = tunnelSettings(
        appModeRepository.mode.value,
        vpnPreferences.ipv6Enabled.value,
        vpnPreferences.excludedPackages.value,
    )

    private fun tunnelSettings(mode: AppMode, ipv6Enabled: Boolean, excludedPackages: Set<String>) = TunnelSettings(
        advancedMode = mode == AppMode.ADVANCED,
        ipv6Enabled = ipv6Enabled,
        excludedPackages = excludedPackages,
    )
}
