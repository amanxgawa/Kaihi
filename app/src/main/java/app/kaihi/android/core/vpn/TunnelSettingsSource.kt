package app.kaihi.android.core.vpn

import kotlinx.coroutines.flow.Flow

interface TunnelSettingsSource {
    val settings: Flow<TunnelSettings>
    fun current(): TunnelSettings
}
