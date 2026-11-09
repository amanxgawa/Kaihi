package app.kaihi.android.feature.importing.impl.domain

import app.kaihi.android.core.storage.AppStorage
import app.kaihi.android.core.storage.ExperienceKeys
import app.kaihi.android.core.storage.StorageDomain
import app.kaihi.android.core.vpn.ImportInput
import app.kaihi.android.core.vpn.SubscriptionFetchFailure
import app.kaihi.android.core.vpn.SubscriptionFetchResult
import app.kaihi.android.core.vpn.SubscriptionFetcher
import app.kaihi.android.core.vpn.VlessLinkResult
import app.kaihi.android.core.vpn.XraySubscription
import app.kaihi.android.core.vpn.XraySubscriptionSource
import app.kaihi.android.core.vpn.XraySubscriptionStore
import app.kaihi.android.core.vpn.XrayVpnProfile
import app.kaihi.android.core.vpn.classifyImportInput
import app.kaihi.android.core.vpn.newXrayId
import app.kaihi.android.core.vpn.parseVlessLink
import java.net.URI
import javax.inject.Inject
import kotlinx.coroutines.flow.first

internal data class ImportCounts(
    val added: Int = 0,
    val duplicates: Int = 0,
    val unsupported: Int = 0,
    val invalid: Int = 0,
    val truncated: Boolean = false,
)

internal sealed interface ImportOutcome {
    data class Success(val counts: ImportCounts, val firstAddedServerId: String) : ImportOutcome
    data class AlreadyAdded(val subscriptionId: String) : ImportOutcome
    data class FetchFailed(val reason: SubscriptionFetchFailure) : ImportOutcome
    data class NothingImportable(val counts: ImportCounts) : ImportOutcome
    data object InsecureUrl : ImportOutcome
    data object Unrecognized : ImportOutcome
}

internal class ImportCoordinator @Inject constructor(
    private val subscriptionStore: XraySubscriptionStore,
    private val subscriptionFetcher: SubscriptionFetcher,
    private val appStorage: AppStorage,
) {
    suspend fun import(text: String): ImportOutcome {
        val nowMillis = System.currentTimeMillis()
        return when (val input = classifyImportInput(text)) {
            is ImportInput.Links -> importLinks(input.lines, nowMillis)
            is ImportInput.RemoteSubscription -> importRemote(input.url, nowMillis)
            is ImportInput.InsecureUrl -> ImportOutcome.InsecureUrl
            ImportInput.Unrecognized -> ImportOutcome.Unrecognized
        }
    }

    private suspend fun importLinks(lines: List<String>, nowMillis: Long): ImportOutcome {
        val parsed = parseLinks(lines, nowMillis)
        if (parsed.profiles.isEmpty()) {
            return ImportOutcome.NothingImportable(parsed.counts)
        }

        var counts = parsed.counts
        var firstAddedServerId: String? = null
        subscriptionStore.update { subscriptions ->
            val imported = subscriptions.firstOrNull { it.id == XraySubscription.IMPORTED_ID }
            val knownProfiles = imported?.servers.orEmpty().toMutableList()
            val added = mutableListOf<XrayVpnProfile>()
            var duplicates = 0
            parsed.profiles.forEach { profile ->
                if (knownProfiles.any { it.matchesImport(profile) }) {
                    duplicates++
                } else {
                    knownProfiles += profile
                    added += profile
                }
            }

            counts = parsed.counts.copy(added = added.size, duplicates = duplicates)
            firstAddedServerId = added.firstOrNull()?.id
            if (added.isEmpty()) {
                subscriptions
            } else {
                val updatedImported = XraySubscription(
                    id = XraySubscription.IMPORTED_ID,
                    name = "",
                    servers = knownProfiles,
                    source = XraySubscriptionSource.Imported,
                )
                if (imported == null) subscriptions + updatedImported
                else subscriptions.map { if (it.id == imported.id) updatedImported else it }
            }
        }

        val firstId = firstAddedServerId ?: return ImportOutcome.NothingImportable(counts)
        markWizardCompleted()
        return ImportOutcome.Success(counts, firstId)
    }

    private suspend fun importRemote(url: String, nowMillis: Long): ImportOutcome {
        val existing = subscriptionStore.subscriptions.first().firstRemoteWithUrl(url)
        if (existing != null) return ImportOutcome.AlreadyAdded(existing.id)

        val fetched = when (val result = subscriptionFetcher.fetch(url)) {
            is SubscriptionFetchResult.Failure -> return ImportOutcome.FetchFailed(result.reason)
            is SubscriptionFetchResult.Success -> result
        }
        val parsed = parseLinks(fetched.links, nowMillis)
        val uniqueProfiles = mutableListOf<XrayVpnProfile>()
        var duplicates = 0
        parsed.profiles.forEach { profile ->
            if (uniqueProfiles.any { it.matchesImport(profile) }) duplicates++ else uniqueProfiles += profile
        }
        val counts = parsed.counts.copy(duplicates = duplicates, truncated = fetched.truncated)
        if (uniqueProfiles.isEmpty()) return ImportOutcome.NothingImportable(counts)

        val uri = URI(url)
        val subscriptionId = newXrayId()
        val metadata = fetched.metadata
        val subscription = XraySubscription(
            id = subscriptionId,
            name = metadata.title ?: requireNotNull(uri.host),
            servers = uniqueProfiles,
            source = XraySubscriptionSource.Remote(
                url = url,
                title = metadata.title,
                userInfo = metadata.userInfo,
                updateIntervalHours = metadata.updateIntervalHours,
                supportUrl = metadata.supportUrl,
                webPageUrl = metadata.webPageUrl,
                announce = metadata.announce,
                lastUpdatedMillis = nowMillis,
            ),
        )

        var outcome: ImportOutcome? = null
        subscriptionStore.update { subscriptions ->
            val concurrentlyAdded = subscriptions.firstRemoteWithUrl(url)
            if (concurrentlyAdded != null) {
                outcome = ImportOutcome.AlreadyAdded(concurrentlyAdded.id)
                subscriptions
            } else {
                outcome = ImportOutcome.Success(counts.copy(added = uniqueProfiles.size), uniqueProfiles.first().id)
                subscriptions + subscription
            }
        }

        return when (val result = requireNotNull(outcome)) {
            is ImportOutcome.Success -> {
                markWizardCompleted()
                result
            }
            else -> result
        }
    }

    private fun parseLinks(lines: List<String>, nowMillis: Long): ParsedLinks {
        val profiles = mutableListOf<XrayVpnProfile>()
        var unsupported = 0
        var invalid = 0
        lines.forEach { line ->
            when (val result = parseVlessLink(line, nowMillis)) {
                is VlessLinkResult.Success -> profiles += result.profile
                is VlessLinkResult.Unsupported -> unsupported++
                is VlessLinkResult.Invalid -> invalid++
            }
        }
        return ParsedLinks(
            profiles = profiles,
            counts = ImportCounts(unsupported = unsupported, invalid = invalid),
        )
    }

    private fun markWizardCompleted() {
        appStorage.putBoolean(StorageDomain.EXPERIENCE, ExperienceKeys.WIZARD_COMPLETED, true)
    }

    private fun List<XraySubscription>.firstRemoteWithUrl(url: String): XraySubscription? =
        firstOrNull { (it.source as? XraySubscriptionSource.Remote)?.url == url }

    private fun XrayVpnProfile.matchesImport(other: XrayVpnProfile): Boolean =
        serverAddress == other.serverAddress && serverPort == other.serverPort && uuid == other.uuid

    private data class ParsedLinks(
        val profiles: List<XrayVpnProfile>,
        val counts: ImportCounts,
    )
}
