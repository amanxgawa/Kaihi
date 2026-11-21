package app.kaihi.android.core.vpn

import app.kaihi.android.BuildConfig
import app.kaihi.android.core.storage.AppStorage
import app.kaihi.android.core.storage.ExperienceKeys
import app.kaihi.android.core.storage.StorageDomain
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class VpnPreferences @Inject constructor(
    private val appStorage: AppStorage,
) {

    private val _ipv6Enabled = MutableStateFlow(
        appStorage.getBoolean(StorageDomain.EXPERIENCE, ExperienceKeys.IPV6_ENABLED),
    )
    val ipv6Enabled: StateFlow<Boolean> = _ipv6Enabled.asStateFlow()

    private val _excludedPackages = MutableStateFlow(readExcludedPackages())
    val excludedPackages: StateFlow<Set<String>> = _excludedPackages.asStateFlow()

    private val _subscriptionUserAgent = MutableStateFlow(readSubscriptionUserAgent())
    val subscriptionUserAgent: StateFlow<String?> = _subscriptionUserAgent.asStateFlow()

    fun setIpv6Enabled(enabled: Boolean) {
        appStorage.putBoolean(StorageDomain.EXPERIENCE, ExperienceKeys.IPV6_ENABLED, enabled)
        _ipv6Enabled.value = enabled
    }

    fun setExcludedPackages(packages: Set<String>) {
        val filteredPackages = packages - BuildConfig.APPLICATION_ID
        appStorage.putString(
            StorageDomain.EXPERIENCE,
            ExperienceKeys.EXCLUDED_PACKAGES,
            filteredPackages.sorted().joinToString(PACKAGE_SEPARATOR),
        )
        _excludedPackages.value = filteredPackages
    }

    fun setSubscriptionUserAgent(value: String?): Boolean {
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
