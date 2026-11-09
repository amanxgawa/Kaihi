package app.kaihi.android.core.vpn

import kotlinx.coroutines.flow.StateFlow

interface VpnPreferences {
    val ipv6Enabled: StateFlow<Boolean>
    val excludedPackages: StateFlow<Set<String>>
    fun setIpv6Enabled(enabled: Boolean)
    fun setExcludedPackages(packages: Set<String>)
}
