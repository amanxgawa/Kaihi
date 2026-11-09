package app.kaihi.android.feature.dashboard.impl.domain

import app.kaihi.android.core.vpn.SubscriptionFetchFailure
import app.kaihi.android.core.vpn.SubscriptionFetchResult
import app.kaihi.android.core.vpn.SubscriptionFetcher
import app.kaihi.android.core.vpn.VlessLinkResult
import app.kaihi.android.core.vpn.XraySubscriptionSource
import app.kaihi.android.core.vpn.XraySubscriptionStore
import app.kaihi.android.core.vpn.XrayVpnProfile
import app.kaihi.android.core.vpn.parseVlessLink
import app.kaihi.android.core.vpn.spec.XraySecurity
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

            val fetched = try {
                subscriptionFetcher.fetch(source.url)
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

            val refreshedAtMillis = nowMillis ?: System.currentTimeMillis()
            val parsedProfiles = success.links.mapNotNull { link ->
                (parseVlessLink(link, refreshedAtMillis) as? VlessLinkResult.Success)?.profile
            }.distinctBy { it.identity() }
            if (parsedProfiles.isEmpty()) return RemoteSubscriptionRefreshOutcome.NothingImportable

            var outcome: RemoteSubscriptionRefreshOutcome = RemoteSubscriptionRefreshOutcome.SubscriptionUnavailable
            subscriptionStore.update { subscriptions ->
                val current = subscriptions.firstOrNull { it.id == subscriptionId }
                val currentSource = current?.source as? XraySubscriptionSource.Remote
                if (current == null || currentSource == null || currentSource.url != source.url) {
                    subscriptions
                } else {
                    val existingByIdentity = current.servers.associateByFirst { it.identity() }
                    val mergedServers = parsedProfiles.map { profile ->
                        existingByIdentity[profile.identity()]?.let(profile::keepUserChoices) ?: profile
                    }
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

private fun XrayVpnProfile.identity() = ServerIdentity(serverAddress, serverPort, uuid)

private data class ServerIdentity(
    val address: String,
    val port: Int,
    val uuid: String,
)

private fun List<XrayVpnProfile>.associateByFirst(key: (XrayVpnProfile) -> ServerIdentity): Map<ServerIdentity, XrayVpnProfile> {
    val result = LinkedHashMap<ServerIdentity, XrayVpnProfile>()
    forEach { result.putIfAbsent(key(it), it) }
    return result
}

private fun XrayVpnProfile.keepUserChoices(existing: XrayVpnProfile): XrayVpnProfile = copy(
    id = existing.id,
    security = when {
        security is XraySecurity.Reality && existing.security is XraySecurity.Reality ->
            security.copy(fingerprint = existing.security.fingerprint)
        else -> security
    },
    addedAtMillis = existing.addedAtMillis,
    countryCode = existing.countryCode,
    muxEnabled = existing.muxEnabled,
    muxConcurrency = existing.muxConcurrency,
    fragmentEnabled = existing.fragmentEnabled,
)

private const val DEFAULT_UPDATE_INTERVAL_HOURS = 24
private const val MILLIS_PER_HOUR = 60L * 60L * 1000L
