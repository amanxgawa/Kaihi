package app.kaihi.android.feature.settings.impl.domain

import app.kaihi.android.core.mode.AppMode
import app.kaihi.android.core.mode.AppModeRepository
import app.kaihi.android.core.vpn.VpnPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

/** The preferences the settings screens edit: app mode and tunnel preferences. */
internal class SettingsPreferences @Inject constructor(
    private val appModeRepository: AppModeRepository,
    private val vpnPreferences: VpnPreferences,
) {
    val mode: StateFlow<AppMode> get() = appModeRepository.mode
    val ipv6Enabled: StateFlow<Boolean> get() = vpnPreferences.ipv6Enabled
    val excludedPackages: StateFlow<Set<String>> get() = vpnPreferences.excludedPackages
    val subscriptionUserAgent: StateFlow<String?> get() = vpnPreferences.subscriptionUserAgent

    fun setMode(mode: AppMode) = appModeRepository.setMode(mode)
    fun setIpv6Enabled(enabled: Boolean) = vpnPreferences.setIpv6Enabled(enabled)
    fun setExcludedPackages(packages: Set<String>) = vpnPreferences.setExcludedPackages(packages)
    fun setSubscriptionUserAgent(value: String?): Boolean = vpnPreferences.setSubscriptionUserAgent(value)
}
