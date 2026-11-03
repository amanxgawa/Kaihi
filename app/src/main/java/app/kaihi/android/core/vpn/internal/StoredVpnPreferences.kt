package app.kaihi.android.core.vpn.internal

import app.kaihi.android.core.storage.AppStorage
import app.kaihi.android.core.storage.ExperienceKeys
import app.kaihi.android.core.storage.StorageDomain
import app.kaihi.android.core.vpn.VpnPreferences
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
internal class StoredVpnPreferences @Inject constructor(
    private val appStorage: AppStorage,
) : VpnPreferences {

    private val _ipv6Enabled = MutableStateFlow(
        appStorage.getBoolean(StorageDomain.EXPERIENCE, ExperienceKeys.IPV6_ENABLED),
    )
    override val ipv6Enabled: StateFlow<Boolean> = _ipv6Enabled.asStateFlow()

    override fun setIpv6Enabled(enabled: Boolean) {
        appStorage.putBoolean(StorageDomain.EXPERIENCE, ExperienceKeys.IPV6_ENABLED, enabled)
        _ipv6Enabled.value = enabled
    }
}
