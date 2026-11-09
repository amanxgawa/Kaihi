package app.kaihi.android.core.vpn

import kotlinx.coroutines.flow.StateFlow

interface VpnPreferences {
    val ipv6Enabled: StateFlow<Boolean>
    val excludedPackages: StateFlow<Set<String>>
    val subscriptionUserAgent: StateFlow<String?>
    fun setIpv6Enabled(enabled: Boolean)
    fun setExcludedPackages(packages: Set<String>)
    fun setSubscriptionUserAgent(value: String?): Boolean
}
