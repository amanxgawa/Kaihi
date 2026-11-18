package app.kaihi.android.feature.importing.impl.domain

import app.kaihi.android.core.storage.AppStorage
import app.kaihi.android.core.storage.ExperienceKeys
import app.kaihi.android.core.storage.StorageDomain
import app.kaihi.android.core.vpn.SubscriptionFetchFailure
import app.kaihi.android.core.vpn.SubscriptionFetchResult
import app.kaihi.android.core.vpn.SubscriptionFetcher
import app.kaihi.android.core.vpn.SubscriptionMetadata
import app.kaihi.android.core.vpn.VlessLinkResult
import app.kaihi.android.core.vpn.XraySubscription
import app.kaihi.android.core.vpn.XraySubscriptionSource
import app.kaihi.android.core.vpn.XraySubscriptionStore
import app.kaihi.android.core.vpn.XraySubscriptionUserInfo
import app.kaihi.android.core.vpn.XrayVpnProfile
import app.kaihi.android.core.vpn.parseVlessLink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportCoordinatorTest {
    @Test
    fun `several links create Imported group with parsed counts`() = runTest {
        val (coordinator, store, fetcher, storage) = fixture()

        val preview = preparePreview(coordinator, "$REALITY_RAW_LINK\n$REALITY_XHTTP_LINK\n$UNSUPPORTED_LINK")
        val outcome = coordinator.add(preview)

        assertTrue(outcome is ImportOutcome.Success)
        val success = outcome as ImportOutcome.Success
        assertEquals(2, success.counts.added)
        assertEquals(0, success.counts.duplicates)
        assertEquals(1, success.counts.unsupported)
        assertEquals(0, success.counts.invalid)
        assertEquals(success.firstAddedServerId, store.current().single().servers.first().id)
        val imported = store.current().single()
        assertEquals(XraySubscription.IMPORTED_ID, imported.id)
        assertEquals("", imported.name)
        assertEquals(XraySubscriptionSource.Imported, imported.source)
        assertEquals(2, imported.servers.size)
        assertTrue(storage.completed)
        assertEquals(0, fetcher.calls)
    }

    @Test
    fun `importing the same link again counts a duplicate without writing`() = runTest {
        val (coordinator, store) = fixture()
        coordinator.add(preparePreview(coordinator, REALITY_RAW_LINK))
        val writesBeforeDuplicate = store.writes

        val preview = preparePreview(coordinator, REALITY_RAW_LINK)
        assertEquals(1, preview.counts.duplicates)
        val outcome = coordinator.add(preview)

        assertTrue(outcome is ImportOutcome.NothingNew)
        val nothingNew = outcome as ImportOutcome.NothingNew
        assertEquals(0, nothingNew.counts.added)
        assertEquals(1, nothingNew.counts.duplicates)
        assertEquals(writesBeforeDuplicate, store.writes)
        assertEquals(1, store.current().single().servers.size)
    }

    @Test
    fun `unsupported and invalid links are counted and nothing is stored`() = runTest {
        val (coordinator, store, _, storage) = fixture()

        val preparation = coordinator.prepare("$UNSUPPORTED_LINK\n$INVALID_LINK")

        assertTrue(preparation is ImportPreparation.NothingImportable)
        val counts = (preparation as ImportPreparation.NothingImportable).counts
        assertEquals(0, counts.added)
        assertEquals(1, counts.unsupported)
        assertEquals(1, counts.invalid)
        assertTrue(store.current().isEmpty())
        assertFalse(storage.completed)
    }

    @Test
    fun `remote success maps metadata and uses its title as the group name`() = runTest {
        val metadata = SubscriptionMetadata(
            title = "Transit",
            userInfo = XraySubscriptionUserInfo(uploadBytes = 10, downloadBytes = 20, totalBytes = 30),
            updateIntervalHours = 12,
            supportUrl = "https://support.example/help",
            webPageUrl = "https://support.example/",
            announce = "Updated",
        )
        val (coordinator, store, fetcher, storage) = fixture(
            fetchResult = SubscriptionFetchResult.Success(
                links = listOf(REALITY_RAW_LINK, REALITY_XHTTP_LINK),
                metadata = metadata,
                truncated = true,
            ),
        )

        val preview = preparePreview(coordinator, SUBSCRIPTION_URL) as ImportPreview.Subscription
        val outcome = coordinator.add(preview)

        assertTrue(outcome is ImportOutcome.Success)
        val success = outcome as ImportOutcome.Success
        assertEquals(2, success.counts.added)
        assertTrue(success.counts.truncated)
        val subscription = store.current().single()
        assertEquals("Transit", subscription.name)
        val source = subscription.source as XraySubscriptionSource.Remote
        assertEquals(SUBSCRIPTION_URL, source.url)
        assertEquals(metadata.title, source.title)
        assertEquals(metadata.userInfo, source.userInfo)
        assertEquals(metadata.updateIntervalHours, source.updateIntervalHours)
        assertEquals(metadata.supportUrl, source.supportUrl)
        assertEquals(metadata.webPageUrl, source.webPageUrl)
        assertEquals(metadata.announce, source.announce)
        assertEquals(preview.fetchedAtMillis, source.lastUpdatedMillis)
        assertNotNull(source.lastUpdatedMillis)
        assertTrue(storage.completed)
        assertEquals(listOf(SUBSCRIPTION_URL), fetcher.urls)
    }

    @Test
    fun `remote without title uses URL host as the group name`() = runTest {
        val (coordinator, store) = fixture(
            fetchResult = SubscriptionFetchResult.Success(
                links = listOf(REALITY_RAW_LINK),
                metadata = SubscriptionMetadata(),
                truncated = false,
            ),
        )

        val outcome = coordinator.add(preparePreview(coordinator, SUBSCRIPTION_URL))

        assertTrue(outcome is ImportOutcome.Success)
        assertEquals("sub.example", store.current().single().name)
    }

    @Test
    fun `existing remote URL returns AlreadyAdded without fetching`() = runTest {
        val profile = parseProfile(REALITY_RAW_LINK)
        val existing = XraySubscription(
            id = "existing-subscription",
            name = "Existing",
            servers = listOf(profile),
            source = XraySubscriptionSource.Remote(url = SUBSCRIPTION_URL),
        )
        val (coordinator, _, fetcher, storage) = fixture(initial = listOf(existing))

        val preparation = coordinator.prepare(SUBSCRIPTION_URL)

        assertEquals(ImportPreparation.AlreadyAdded(existing.id, existing.name, profile.id), preparation)
        assertEquals(0, fetcher.calls)
        assertFalse(storage.completed)
    }

    @Test
    fun `fetch failure does not store or mark setup complete`() = runTest {
        val (coordinator, store, fetcher, storage) = fixture(
            fetchResult = SubscriptionFetchResult.Failure(SubscriptionFetchFailure.TIMEOUT),
        )

        val preparation = coordinator.prepare(SUBSCRIPTION_URL)

        assertEquals(ImportPreparation.FetchFailed(SubscriptionFetchFailure.TIMEOUT), preparation)
        assertTrue(store.current().isEmpty())
        assertEquals(0, store.writes)
        assertFalse(storage.completed)
        assertEquals(1, fetcher.calls)
    }

    @Test
    fun `wizard completed flag is written only after an import succeeds`() = runTest {
        val (coordinator, _, _, storage) = fixture(
            fetchResult = SubscriptionFetchResult.Failure(SubscriptionFetchFailure.NETWORK),
        )

        coordinator.prepare("not a VPN link")
        assertFalse(storage.completed)
        coordinator.prepare(SUBSCRIPTION_URL)
        assertFalse(storage.completed)
        coordinator.add(preparePreview(coordinator, REALITY_RAW_LINK))
        assertTrue(storage.completed)
        assertEquals(StorageDomain.EXPERIENCE to ExperienceKeys.WIZARD_COMPLETED, storage.lastBooleanKey)
    }

    @Test
    fun `prepare writes nothing`() = runTest {
        val (linksCoordinator, linksStore, _, linksStorage) = fixture()

        assertTrue(linksCoordinator.prepare(REALITY_RAW_LINK) is ImportPreparation.Ready)
        assertEquals(0, linksStore.writes)
        assertFalse(linksStorage.completed)

        val (remoteCoordinator, remoteStore, _, remoteStorage) = fixture(
            fetchResult = SubscriptionFetchResult.Success(
                links = listOf(REALITY_RAW_LINK),
                metadata = SubscriptionMetadata(),
                truncated = false,
            ),
        )
        assertTrue(remoteCoordinator.prepare(SUBSCRIPTION_URL) is ImportPreparation.Ready)
        assertEquals(0, remoteStore.writes)
        assertFalse(remoteStorage.completed)
    }

    @Test
    fun `add saves exactly the previewed subscription without fetching again`() = runTest {
        val (coordinator, store, fetcher) = fixture(
            fetchResults = listOf(
                SubscriptionFetchResult.Success(
                    links = listOf(REALITY_RAW_LINK),
                    metadata = SubscriptionMetadata(title = "First response"),
                    truncated = false,
                ),
                SubscriptionFetchResult.Success(
                    links = listOf(REALITY_XHTTP_LINK),
                    metadata = SubscriptionMetadata(title = "Second response"),
                    truncated = false,
                ),
            ),
        )

        val preview = preparePreview(coordinator, SUBSCRIPTION_URL) as ImportPreview.Subscription
        val outcome = coordinator.add(preview)

        assertTrue(outcome is ImportOutcome.Success)
        assertEquals(1, fetcher.calls)
        assertEquals(preview.profiles, store.current().single().servers)
    }

    @Test
    fun `add of links recounts duplicates against the current store`() = runTest {
        val (coordinator, store) = fixture()
        val preview = preparePreview(coordinator, "$REALITY_RAW_LINK\n$REALITY_XHTTP_LINK") as ImportPreview.Links
        val concurrentlyAdded = preview.profiles.first()
        store.update { subscriptions ->
            subscriptions + XraySubscription(
                id = XraySubscription.IMPORTED_ID,
                name = "",
                servers = listOf(concurrentlyAdded),
                source = XraySubscriptionSource.Imported,
            )
        }

        val outcome = coordinator.add(preview)

        assertTrue(outcome is ImportOutcome.Success)
        val counts = (outcome as ImportOutcome.Success).counts
        assertEquals(1, counts.added)
        assertEquals(1, counts.duplicates)
    }

    @Test
    fun `prepare of links already stored returns Ready with duplicates counted`() = runTest {
        val existing = XraySubscription(
            id = XraySubscription.IMPORTED_ID,
            name = "",
            servers = listOf(parseProfile(REALITY_RAW_LINK)),
            source = XraySubscriptionSource.Imported,
        )
        val (coordinator, _) = fixture(initial = listOf(existing))

        val preview = preparePreview(coordinator, REALITY_RAW_LINK)
        assertEquals(1, preview.counts.duplicates)
        assertTrue(coordinator.add(preview) is ImportOutcome.NothingNew)
    }

    @Test
    fun `subscription preview toString hides the url`() {
        val preview = ImportPreview.Subscription(
            url = "https://sub.example/s/SECRET_TOKEN",
            host = "sub.example",
            metadata = SubscriptionMetadata(),
            profiles = listOf(parseProfile(REALITY_RAW_LINK)),
            counts = ImportCounts(),
            fetchedAtMillis = 123L,
        )

        assertFalse(preview.toString().contains("SECRET_TOKEN"))
    }

    private suspend fun preparePreview(coordinator: ImportCoordinator, text: String): ImportPreview {
        val preparation = coordinator.prepare(text)
        assertTrue("Expected a ready preview, got $preparation", preparation is ImportPreparation.Ready)
        return (preparation as ImportPreparation.Ready).preview
    }

    private fun parseProfile(link: String): XrayVpnProfile =
        (parseVlessLink(link, 1L) as VlessLinkResult.Success).profile

    private fun fixture(
        initial: List<XraySubscription> = emptyList(),
        fetchResult: SubscriptionFetchResult = SubscriptionFetchResult.Failure(SubscriptionFetchFailure.EMPTY),
        fetchResults: List<SubscriptionFetchResult> = listOf(fetchResult),
    ): Fixture {
        val store = MemorySubscriptionStore(initial)
        val fetcher = FakeSubscriptionFetcher(fetchResults)
        val storage = MemoryAppStorage()
        return Fixture(ImportCoordinator(store, fetcher, storage), store, fetcher, storage)
    }

    private data class Fixture(
        val coordinator: ImportCoordinator,
        val store: MemorySubscriptionStore,
        val fetcher: FakeSubscriptionFetcher,
        val storage: MemoryAppStorage,
    )

    private class MemorySubscriptionStore(initial: List<XraySubscription>) : XraySubscriptionStore {
        private val state = MutableStateFlow(initial)
        override val subscriptions: Flow<List<XraySubscription>> = state
        var writes = 0
            private set

        fun current(): List<XraySubscription> = state.value

        override suspend fun update(
            transform: (List<XraySubscription>) -> List<XraySubscription>,
        ): List<XraySubscription> {
            val next = transform(state.value)
            if (next != state.value) {
                writes++
                state.value = next
            }
            return next
        }
    }

    private class FakeSubscriptionFetcher(
        private val results: List<SubscriptionFetchResult>,
    ) : SubscriptionFetcher {
        var calls = 0
            private set
        val urls = mutableListOf<String>()

        override suspend fun fetch(url: String): SubscriptionFetchResult {
            calls++
            urls += url
            return results.getOrElse(calls - 1) { results.last() }
        }
    }

    private class MemoryAppStorage : AppStorage {
        var completed = false
        var lastBooleanKey: Pair<StorageDomain, String>? = null

        override fun getString(domain: StorageDomain, key: String): String? = null
        override fun putString(domain: StorageDomain, key: String, value: String) = Unit
        override fun getBoolean(domain: StorageDomain, key: String): Boolean = completed
        override fun putBoolean(domain: StorageDomain, key: String, value: Boolean) {
            lastBooleanKey = domain to key
            completed = value
        }
        override fun remove(domain: StorageDomain, key: String) = Unit
    }

    private companion object {
        const val SUBSCRIPTION_URL = "https://sub.example/config"
        const val REALITY_RAW_LINK =
            "vless://a1b2c3d4-e5f6-7890-abcd-ef1234567890@203.0.113.8:443" +
                "?type=raw&security=reality&encryption=none&sni=cdn.example.com&pbk=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA&sid=0011" +
                "&flow=xtls-rprx-vision#Panel%20node"
        const val REALITY_XHTTP_LINK =
            "vless://a1b2c3d4-e5f6-7890-abcd-ef1234567890@node.example:443" +
                "?security=reality&sni=cover.example&pbk=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA&sid=" +
                "&fp=firefox&type=xhttp&path=%2Fedge%2Fconnect&mode=packet-up&host=cover.example#EU%20%F0%9F%8C%90"
        const val UNSUPPORTED_LINK =
            "vmess://a1b2c3d4-e5f6-7890-abcd-ef1234567890@example.com:443"
        const val INVALID_LINK =
            "vless://a1b2c3d4-e5f6-7890-abcd-ef1234567890@example.com:443?security=reality&pbk=short&sni=example.com"
    }
}
