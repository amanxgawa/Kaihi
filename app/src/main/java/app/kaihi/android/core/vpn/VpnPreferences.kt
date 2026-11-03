package app.kaihi.android.core.vpn

import kotlinx.coroutines.flow.StateFlow

interface VpnPreferences {
    val ipv6Enabled: StateFlow<Boolean>
    fun setIpv6Enabled(enabled: Boolean)
}
