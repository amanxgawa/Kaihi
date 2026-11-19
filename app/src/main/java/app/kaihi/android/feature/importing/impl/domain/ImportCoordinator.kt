package app.kaihi.android.feature.importing.impl.domain

import app.kaihi.android.core.mode.OnboardingState
import app.kaihi.android.core.vpn.ImportInput
import app.kaihi.android.core.vpn.SubscriptionFetchFailure
import app.kaihi.android.core.vpn.SubscriptionFetchResult
import app.kaihi.android.core.vpn.SubscriptionFetcher
import app.kaihi.android.core.vpn.SubscriptionMetadata
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

internal sealed interface ImportPreview {
    val profiles: List<XrayVpnProfile>
    val counts: ImportCounts

    /** Pasted vless:// links. `counts.duplicates` includes links already in Imported at prepare time. */
    data class Links(
        override val profiles: List<XrayVpnProfile>,
        override val counts: ImportCounts,
    ) : ImportPreview {
        override fun toString(): String = "Links(profileCount=${profiles.size}, counts=$counts)"
    }

    /** An HTTPS subscription, fetched once. */
    data class Subscription(
        val url: String,
        val host: String,
        val metadata: SubscriptionMetadata,
        override val profiles: List<XrayVpnProfile>,
        override val counts: ImportCounts,
        val fetchedAtMillis: Long,
    ) : ImportPreview {
        override fun toString(): String =
            "Subscription(url=<redacted>, host=$host, metadata=$metadata, profileCount=${profiles.size}, " +
                "counts=$counts, fetchedAtMillis=$fetchedAtMillis)"
    }
}

internal sealed interface ImportPreparation {
    data class Ready(val preview: ImportPreview) : ImportPreparation
    data class AlreadyAdded(val name: String, val firstServerId: String?) : ImportPreparation
    data class FetchFailed(val reason: SubscriptionFetchFailure) : ImportPreparation
    data class NothingImportable(val counts: ImportCounts) : ImportPreparation
    data object InsecureUrl : ImportPreparation
    data object Unrecognized : ImportPreparation
}

internal sealed interface ImportOutcome {
    data class Success(val counts: ImportCounts, val firstAddedServerId: String) : ImportOutcome
    data object AlreadyAdded : ImportOutcome
    data class NothingNew(val counts: ImportCounts) : ImportOutcome
}

internal class ImportCoordinator @Inject constructor(
    private val subscriptionStore: XraySubscriptionStore,
    private val subscriptionFetcher: SubscriptionFetcher,
    private val onboardingState: OnboardingState,
) {
    suspend fun prepare(text: String): ImportPreparation {
        val nowMillis = System.currentTimeMillis()
        return when (val input = classifyImportInput(text)) {
            is ImportInput.Links -> prepareLinks(input.lines, nowMillis)
            is ImportInput.RemoteSubscription -> prepareRemote(input.url, nowMillis)
            is ImportInput.InsecureUrl -> ImportPreparation.InsecureUrl
            ImportInput.Unrecognized -> ImportPreparation.Unrecognized
        }
    }

    suspend fun add(preview: ImportPreview): ImportOutcome = when (preview) {
        is ImportPreview.Links -> addLinks(preview)
        is ImportPreview.Subscription -> addSubscription(preview)
    }

    private suspend fun prepareLinks(lines: List<String>, nowMillis: Long): ImportPreparation {
        val parsed = parseLinks(lines, nowMillis)
        if (parsed.profiles.isEmpty()) {
            return ImportPreparation.NothingImportable(parsed.counts)
        }

        val importedProfiles = subscriptionStore.subscriptions.first()
            .firstOrNull { it.id == XraySubscription.IMPORTED_ID }
            ?.servers.orEmpty()
        val uniqueProfiles = mutableListOf<XrayVpnProfile>()
        var duplicates = 0
        parsed.profiles.forEach { profile ->
            if (uniqueProfiles.any { it.matchesImport(profile) }) {
                duplicates++
            } else {
                uniqueProfiles += profile
                if (importedProfiles.any { it.matchesImport(profile) }) duplicates++
            }
        }
        return ImportPreparation.Ready(
            ImportPreview.Links(
                profiles = uniqueProfiles,
                counts = parsed.counts.copy(duplicates = duplicates),
            ),
        )
    }

    private suspend fun prepareRemote(url: String, nowMillis: Long): ImportPreparation {
        val existing = subscriptionStore.subscriptions.first().firstRemoteWithUrl(url)
        if (existing != null) {
            return ImportPreparation.AlreadyAdded(existing.name, existing.servers.firstOrNull()?.id)
        }

        val fetched = when (val result = subscriptionFetcher.fetch(url)) {
            is SubscriptionFetchResult.Failure -> return ImportPreparation.FetchFailed(result.reason)
            is SubscriptionFetchResult.Success -> result
        }
        val parsed = parseLinks(fetched.links, nowMillis)
        val uniqueProfiles = mutableListOf<XrayVpnProfile>()
        var duplicates = 0
        parsed.profiles.forEach { profile ->
            if (uniqueProfiles.any { it.matchesImport(profile) }) duplicates++ else uniqueProfiles += profile
        }
        val counts = parsed.counts.copy(duplicates = duplicates, truncated = fetched.truncated)
        if (uniqueProfiles.isEmpty()) return ImportPreparation.NothingImportable(counts)

        return ImportPreparation.Ready(
            ImportPreview.Subscription(
                url = url,
                host = requireNotNull(URI(url).host),
                metadata = fetched.metadata,
                profiles = uniqueProfiles,
                counts = counts,
                fetchedAtMillis = nowMillis,
            ),
        )
    }

    private suspend fun addLinks(preview: ImportPreview.Links): ImportOutcome {
        var counts = preview.counts
        var firstAddedServerId: String? = null
        subscriptionStore.update { subscriptions ->
            val imported = subscriptions.firstOrNull { it.id == XraySubscription.IMPORTED_ID }
            val knownProfiles = imported?.servers.orEmpty().toMutableList()
            val added = mutableListOf<XrayVpnProfile>()
            var duplicates = 0
            preview.profiles.forEach { profile ->
                if (knownProfiles.any { it.matchesImport(profile) }) {
                    duplicates++
                } else {
                    knownProfiles += profile
                    added += profile
                }
            }

            counts = preview.counts.copy(added = added.size, duplicates = duplicates)
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

        val firstId = firstAddedServerId ?: return ImportOutcome.NothingNew(counts)
        markWizardCompleted()
        return ImportOutcome.Success(counts, firstId)
    }

    private suspend fun addSubscription(preview: ImportPreview.Subscription): ImportOutcome {
        val subscriptionId = newXrayId()
        val metadata = preview.metadata
        val subscription = XraySubscription(
            id = subscriptionId,
            name = metadata.title ?: preview.host,
            servers = preview.profiles,
            source = XraySubscriptionSource.Remote(
                url = preview.url,
                title = metadata.title,
                userInfo = metadata.userInfo,
                updateIntervalHours = metadata.updateIntervalHours,
                supportUrl = metadata.supportUrl,
                webPageUrl = metadata.webPageUrl,
                announce = metadata.announce,
                lastUpdatedMillis = preview.fetchedAtMillis,
            ),
        )

        var outcome: ImportOutcome? = null
        subscriptionStore.update { subscriptions ->
            val concurrentlyAdded = subscriptions.firstRemoteWithUrl(preview.url)
            if (concurrentlyAdded != null) {
                outcome = ImportOutcome.AlreadyAdded
                subscriptions
            } else {
                outcome = ImportOutcome.Success(
                    preview.counts.copy(added = preview.profiles.size),
                    preview.profiles.first().id,
                )
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
        onboardingState.markCompleted()
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
