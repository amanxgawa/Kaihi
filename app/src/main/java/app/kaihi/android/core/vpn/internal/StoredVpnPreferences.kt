package app.kaihi.android.core.vpn.internal

import app.kaihi.android.BuildConfig
import app.kaihi.android.core.storage.AppStorage
import app.kaihi.android.core.storage.ExperienceKeys
import app.kaihi.android.core.storage.StorageDomain
import app.kaihi.android.core.vpn.VpnPreferences
import app.kaihi.android.core.vpn.isValidSubscriptionUserAgent
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

    private val _excludedPackages = MutableStateFlow(readExcludedPackages())
    override val excludedPackages: StateFlow<Set<String>> = _excludedPackages.asStateFlow()

    private val _subscriptionUserAgent = MutableStateFlow(readSubscriptionUserAgent())
    override val subscriptionUserAgent: StateFlow<String?> = _subscriptionUserAgent.asStateFlow()

    override fun setIpv6Enabled(enabled: Boolean) {
        appStorage.putBoolean(StorageDomain.EXPERIENCE, ExperienceKeys.IPV6_ENABLED, enabled)
        _ipv6Enabled.value = enabled
    }

    override fun setExcludedPackages(packages: Set<String>) {
        val filteredPackages = packages - BuildConfig.APPLICATION_ID
        appStorage.putString(
            StorageDomain.EXPERIENCE,
            ExperienceKeys.EXCLUDED_PACKAGES,
            filteredPackages.sorted().joinToString(PACKAGE_SEPARATOR),
        )
        _excludedPackages.value = filteredPackages
    }

    override fun setSubscriptionUserAgent(value: String?): Boolean {
        if (value != null && !isValidSubscriptionUserAgent(value)) return false

        if (value == null) {
            appStorage.remove(StorageDomain.EXPERIENCE, ExperienceKeys.SUBSCRIPTION_USER_AGENT)
        } else {
            appStorage.putString(
                StorageDomain.EXPERIENCE,
                ExperienceKeys.SUBSCRIPTION_USER_AGENT,
                value,
            )
        }
        _subscriptionUserAgent.value = value
        return true
    }

    private fun readExcludedPackages(): Set<String> = appStorage.getString(
        StorageDomain.EXPERIENCE,
        ExperienceKeys.EXCLUDED_PACKAGES,
    )
        ?.split(PACKAGE_SEPARATOR)
        ?.filterTo(mutableSetOf()) { it.isNotBlank() && it != BuildConfig.APPLICATION_ID }
        .orEmpty()

    private fun readSubscriptionUserAgent(): String? = appStorage.getString(
        StorageDomain.EXPERIENCE,
        ExperienceKeys.SUBSCRIPTION_USER_AGENT,
    )?.takeIf(::isValidSubscriptionUserAgent)

    private companion object {
        const val PACKAGE_SEPARATOR = ","
    }
}
