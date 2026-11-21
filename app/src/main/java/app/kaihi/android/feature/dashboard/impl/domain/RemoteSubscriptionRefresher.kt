package app.kaihi.android.feature.dashboard.impl.domain

import app.kaihi.android.core.vpn.SubscriptionContent
import app.kaihi.android.core.vpn.SubscriptionFetchFailure
import app.kaihi.android.core.vpn.SubscriptionFetchResult
import app.kaihi.android.core.vpn.SubscriptionFetcher
import app.kaihi.android.core.vpn.ShareLinkResult
import app.kaihi.android.core.vpn.XraySubscriptionSource
import app.kaihi.android.core.vpn.XraySubscriptionStore
import app.kaihi.android.core.vpn.XrayVpnProfile
import app.kaihi.android.core.vpn.parseShareLink
import app.kaihi.android.core.vpn.realityFingerprint
import app.kaihi.android.core.vpn.sharesEndpointWith
import app.kaihi.android.core.vpn.withRealityFingerprint
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

internal sealed interface RemoteSubscriptionRefreshOutcome {
    data class Success(val serverCount: Int) : RemoteSubscriptionRefreshOutcome
    data class FetchFailed(val reason: SubscriptionFetchFailure) : RemoteSubscriptionRefreshOutcome
    data object NothingImportable : RemoteSubscriptionRefreshOutcome
    data object SubscriptionUnavailable : RemoteSubscriptionRefreshOutcome
    data object AlreadyRefreshing : RemoteSubscriptionRefreshOutcome
}

@Singleton
internal class RemoteSubscriptionRefresher @Inject constructor(
    private val subscriptionStore: XraySubscriptionStore,
    private val subscriptionFetcher: SubscriptionFetcher,
) {
    private val refreshingSubscriptionIds = ConcurrentHashMap.newKeySet<String>()

    suspend fun refresh(
        subscriptionId: String,
        nowMillis: Long? = null,
    ): RemoteSubscriptionRefreshOutcome {
        if (!refreshingSubscriptionIds.add(subscriptionId)) {
            return RemoteSubscriptionRefreshOutcome.AlreadyRefreshing
        }

        try {
            val subscription = subscriptionStore.subscriptions.first()
                .firstOrNull { it.id == subscriptionId }
                ?: return RemoteSubscriptionRefreshOutcome.SubscriptionUnavailable
            val source = subscription.source as? XraySubscriptionSource.Remote
                ?: return RemoteSubscriptionRefreshOutcome.SubscriptionUnavailable

            val refreshedAtMillis = nowMillis ?: System.currentTimeMillis()
            val fetched = try {
                subscriptionFetcher.fetch(source.url, refreshedAtMillis)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                return RemoteSubscriptionRefreshOutcome.FetchFailed(SubscriptionFetchFailure.NETWORK)
            }
            val success = when (fetched) {
                is SubscriptionFetchResult.Failure ->
                    return RemoteSubscriptionRefreshOutcome.FetchFailed(fetched.reason)
                is SubscriptionFetchResult.Success -> fetched
            }

            val parsedProfiles = when (val content = success.content) {
                is SubscriptionContent.ShareLinks -> content.links.mapNotNull { link ->
                    (parseShareLink(link, refreshedAtMillis) as? ShareLinkResult.Success)?.profile
                }
                is SubscriptionContent.XrayJson -> content.subscription.profiles
            }
            if (parsedProfiles.isEmpty()) return RemoteSubscriptionRefreshOutcome.NothingImportable

            var outcome: RemoteSubscriptionRefreshOutcome = RemoteSubscriptionRefreshOutcome.SubscriptionUnavailable
            subscriptionStore.update { subscriptions ->
                val current = subscriptions.firstOrNull { it.id == subscriptionId }
                val currentSource = current?.source as? XraySubscriptionSource.Remote
                if (current == null || currentSource == null || currentSource.url != source.url) {
                    subscriptions
                } else {
                    val mergedServers = mergeRefreshedProfiles(parsedProfiles, current.servers)
                    val updatedSource = currentSource.copy(
                        title = success.metadata.title,
                        userInfo = success.metadata.userInfo,
                        updateIntervalHours = success.metadata.updateIntervalHours,
                        supportUrl = success.metadata.supportUrl,
                        webPageUrl = success.metadata.webPageUrl,
                        announce = success.metadata.announce,
                        lastUpdatedMillis = refreshedAtMillis,
                    )
                    outcome = RemoteSubscriptionRefreshOutcome.Success(mergedServers.size)
                    subscriptions.map { item ->
                        if (item.id == subscriptionId) {
                            current.copy(servers = mergedServers, source = updatedSource)
                        } else {
                            item
                        }
                    }
                }
            }
            return outcome
        } finally {
            refreshingSubscriptionIds.remove(subscriptionId)
        }
    }
}

internal fun XraySubscriptionSource.Remote.isStale(nowMillis: Long): Boolean {
    val updatedAt = lastUpdatedMillis ?: return true
    if (nowMillis < updatedAt) return false
    val intervalMillis = (updateIntervalHours ?: DEFAULT_UPDATE_INTERVAL_HOURS).toLong() * MILLIS_PER_HOUR
    return nowMillis - updatedAt >= intervalMillis
}

internal fun mergeRefreshedProfiles(
    parsedProfiles: List<XrayVpnProfile>,
    existingProfiles: List<XrayVpnProfile>,
): List<XrayVpnProfile> {
    val uniqueProfiles = mutableListOf<XrayVpnProfile>()
    parsedProfiles.forEach { profile ->
        if (uniqueProfiles.none { it.sharesEndpointWith(profile) }) uniqueProfiles += profile
    }
    val claimed = BooleanArray(existingProfiles.size)
    val matches = arrayOfNulls<XrayVpnProfile>(uniqueProfiles.size)
    uniqueProfiles.forEachIndexed { index, profile ->
        val match = existingProfiles.indices.firstOrNull {
            !claimed[it] && profile.sharesEndpointWith(existingProfiles[it])
        }
        if (match != null) {
            claimed[match] = true
            matches[index] = existingProfiles[match]
        }
    }
    // Reserve every endpoint match before falling back to names.
    uniqueProfiles.forEachIndexed { index, profile ->
        if (matches[index] == null) {
            val match = existingProfiles.indices.firstOrNull {
                !claimed[it] && profile.name == existingProfiles[it].name
            }
            if (match != null) {
                claimed[match] = true
                matches[index] = existingProfiles[match]
            }
        }
    }
    return uniqueProfiles.mapIndexed { index, profile ->
        matches[index]?.let(profile::keepUserChoices) ?: profile
    }
}

private fun XrayVpnProfile.keepUserChoices(existing: XrayVpnProfile): XrayVpnProfile {
    val existingFingerprint = existing.realityFingerprint
    val profile = if (existingFingerprint == null) {
        this
    } else {
        withRealityFingerprint { existingFingerprint }
    }
    return profile.copy(
        id = existing.id,
        addedAtMillis = existing.addedAtMillis,
        countryCode = existing.countryCode,
        muxEnabled = existing.muxEnabled,
        muxConcurrency = existing.muxConcurrency,
        fragmentEnabled = existing.fragmentEnabled,
    )
}

private const val DEFAULT_UPDATE_INTERVAL_HOURS = 24
private const val MILLIS_PER_HOUR = 60L * 60L * 1000L
