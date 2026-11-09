package app.kaihi.android.feature.dashboard.impl.domain

import app.kaihi.android.core.vpn.SubscriptionFetchFailure
import app.kaihi.android.core.vpn.SubscriptionFetchResult
import app.kaihi.android.core.vpn.SubscriptionFetcher
import app.kaihi.android.core.vpn.SubscriptionMetadata
import app.kaihi.android.core.vpn.XraySubscription
import app.kaihi.android.core.vpn.XraySubscriptionSource
import app.kaihi.android.core.vpn.XraySubscriptionStore
import app.kaihi.android.core.vpn.XraySubscriptionUserInfo
import app.kaihi.android.core.vpn.XrayVpnProfile
import app.kaihi.android.core.vpn.spec.XhttpMode
import app.kaihi.android.core.vpn.spec.XrayFingerprint
import app.kaihi.android.core.vpn.spec.XrayFingerprintSetting
import app.kaihi.android.core.vpn.spec.XraySecurity
import app.kaihi.android.core.vpn.spec.XrayTransport
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteSubscriptionRefresherTest {

    @Test
    fun `matched server keeps id and user choices but takes provider fields`() = runTest {
        val existing = profile(
            id = "saved-id",
            name = "My server name",
            address = "node.example",
            uuid = UUID_ONE,
            security = XraySecurity.Reality(
                serverName = "old-cover.example",
                publicKey = "old-key",
                shortId = "aa11",
                fingerprint = XrayFingerprintSetting.Fixed(XrayFingerprint.FIREFOX),
            ),
            transport = XrayTransport.Raw(),
            addedAtMillis = 123L,
            countryCode = "US",
            muxEnabled = true,
            muxConcurrency = 17,
            fragmentEnabled = true,
        )
        val link = realityLink(UUID_ONE, "node.example", "New name")
            .replace("#New%20name", "&fp=chrome&type=xhttp&path=%2Fprovider&mode=packet-up#New%20name")
        val store = MemoryStore(listOf(subscription(servers = listOf(existing))))
        val refresher = RemoteSubscriptionRefresher(store, FakeFetcher(success(link)))

        val outcome = refresher.refresh(SUBSCRIPTION_ID, nowMillis = NOW)
        val updated = store.value.single().servers.single()

        assertEquals(RemoteSubscriptionRefreshOutcome.Success(1), outcome)
        assertEquals("saved-id", updated.id)
        assertEquals("New name", updated.name)
        assertEquals("node.example", updated.serverAddress)
        assertEquals(
            XraySecurity.Reality(
                serverName = "cover.example",
                publicKey = PUBLIC_KEY,
                shortId = "0011",
                fingerprint = XrayFingerprintSetting.Fixed(XrayFingerprint.FIREFOX),
            ),
            updated.security,
        )
        assertEquals(XrayTransport.Xhttp("/provider", XhttpMode.PACKET_UP), updated.transport)
        assertEquals(123L, updated.addedAtMillis)
        assertEquals("US", updated.countryCode)
        assertTrue(updated.muxEnabled)
        assertEquals(17, updated.muxConcurrency)
        assertTrue(updated.fragmentEnabled)
    }

    @Test
    fun `adds new servers removes missing servers and preserves provider order`() = runTest {
        val matched = profile("saved-a", "Old A", "node-a.example", UUID_ONE)
        val removed = profile("saved-b", "Removed", "node-b.example", UUID_TWO)
        val links = listOf(
            realityLink(UUID_ONE, "node-a.example", "Provider A"),
            realityLink(UUID_THREE, "node-c.example", "Provider C"),
            realityLink(UUID_THREE, "node-c.example", "Duplicate C"),
        )
        val store = MemoryStore(listOf(subscription(servers = listOf(matched, removed))))
        val refresher = RemoteSubscriptionRefresher(store, FakeFetcher(success(*links.toTypedArray())))

        val outcome = refresher.refresh(SUBSCRIPTION_ID, nowMillis = NOW)
        val servers = store.value.single().servers

        assertEquals(RemoteSubscriptionRefreshOutcome.Success(2), outcome)
        assertEquals("saved-a", servers.first().id)
        assertEquals(listOf("node-a.example", "node-c.example"), servers.map { it.serverAddress })
        assertEquals(listOf("Provider A", "Provider C"), servers.map { it.name })
    }

    @Test
    fun `refresh never overwrites a user renamed subscription`() = runTest {
        val store = MemoryStore(listOf(subscription(name = "My chosen name")))
        val refresher = RemoteSubscriptionRefresher(
            store,
            FakeFetcher(success(realityLink(UUID_ONE, "node.example", "Server"), metadata = SubscriptionMetadata(title = "Provider title"))),
        )

        refresher.refresh(SUBSCRIPTION_ID, nowMillis = NOW)

        assertEquals("My chosen name", store.value.single().name)
        assertEquals("Provider title", (store.value.single().source as XraySubscriptionSource.Remote).title)
    }

    @Test
    fun `fetch failure leaves subscription unchanged`() = runTest {
        val initial = subscription(servers = listOf(profile("saved", "Existing", "node.example", UUID_ONE)))
        val store = MemoryStore(listOf(initial))
        val refresher = RemoteSubscriptionRefresher(
            store,
            FakeFetcher(SubscriptionFetchResult.Failure(SubscriptionFetchFailure.TIMEOUT)),
        )

        val outcome = refresher.refresh(SUBSCRIPTION_ID, nowMillis = NOW)

        assertEquals(RemoteSubscriptionRefreshOutcome.FetchFailed(SubscriptionFetchFailure.TIMEOUT), outcome)
        assertEquals(listOf(initial), store.value)
        assertEquals(0, store.updates)
    }

    @Test
    fun `no importable links leaves subscription unchanged`() = runTest {
        val initial = subscription(servers = listOf(profile("saved", "Existing", "node.example", UUID_ONE)))
        val store = MemoryStore(listOf(initial))
        val refresher = RemoteSubscriptionRefresher(store, FakeFetcher(success("not a VLESS link")))

        val outcome = refresher.refresh(SUBSCRIPTION_ID, nowMillis = NOW)

        assertEquals(RemoteSubscriptionRefreshOutcome.NothingImportable, outcome)
        assertEquals(listOf(initial), store.value)
        assertEquals(0, store.updates)
    }

    @Test
    fun `success updates response metadata and last updated timestamp`() = runTest {
        val metadata = SubscriptionMetadata(
            title = "Fresh title",
            userInfo = XraySubscriptionUserInfo(uploadBytes = 10L, downloadBytes = 25L, totalBytes = 200L, expireEpochSeconds = 500L),
            updateIntervalHours = 8,
            supportUrl = "https://support.example/help",
            webPageUrl = "https://support.example/account",
            announce = "Maintenance tonight",
        )
        val store = MemoryStore(listOf(subscription()))
        val refresher = RemoteSubscriptionRefresher(
            store,
            FakeFetcher(success(realityLink(UUID_ONE, "node.example", "Server"), metadata = metadata)),
        )

        val outcome = refresher.refresh(SUBSCRIPTION_ID, nowMillis = NOW)
        val remote = store.value.single().source as XraySubscriptionSource.Remote

        assertTrue(outcome is RemoteSubscriptionRefreshOutcome.Success)
        assertEquals(metadata.title, remote.title)
        assertEquals(metadata.userInfo, remote.userInfo)
        assertEquals(metadata.updateIntervalHours, remote.updateIntervalHours)
        assertEquals(metadata.supportUrl, remote.supportUrl)
        assertEquals(metadata.webPageUrl, remote.webPageUrl)
        assertEquals(metadata.announce, remote.announce)
        assertEquals(NOW, remote.lastUpdatedMillis)
    }

    @Test
    fun `second concurrent refresh for a subscription is ignored`() = runTest {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val fetcher = BlockingFetcher(success(realityLink(UUID_ONE, "node.example", "Server")), started, release)
        val refresher = RemoteSubscriptionRefresher(MemoryStore(listOf(subscription())), fetcher)

        val first = async { refresher.refresh(SUBSCRIPTION_ID, nowMillis = NOW) }
        started.await()
        val second = refresher.refresh(SUBSCRIPTION_ID, nowMillis = NOW)
        release.complete(Unit)

        assertEquals(RemoteSubscriptionRefreshOutcome.AlreadyRefreshing, second)
        assertEquals(RemoteSubscriptionRefreshOutcome.Success(1), first.await())
        assertEquals(1, fetcher.calls)
    }

    @Test
    fun `staleness uses null timestamp and default twenty four hour interval`() {
        val now = 1_000_000_000L
        assertTrue(remote(lastUpdated = null).isStale(now))
        assertFalse(remote(lastUpdated = now - 7_200_000L, intervalHours = 3).isStale(now))
        assertTrue(remote(lastUpdated = now - 86_400_000L, intervalHours = null).isStale(now))
    }

    private fun success(vararg links: String, metadata: SubscriptionMetadata = SubscriptionMetadata()) =
        SubscriptionFetchResult.Success(links.toList(), metadata, truncated = false)

    private fun subscription(
        name: String = "Imported provider",
        servers: List<XrayVpnProfile> = emptyList(),
    ) = XraySubscription(
        id = SUBSCRIPTION_ID,
        name = name,
        servers = servers,
        source = remote(),
    )

    private fun remote(
        lastUpdated: Long? = 1L,
        intervalHours: Int? = 12,
    ) = XraySubscriptionSource.Remote(
        url = SUBSCRIPTION_URL,
        title = "Old title",
        userInfo = XraySubscriptionUserInfo(uploadBytes = 1L, downloadBytes = 2L, totalBytes = 100L),
        updateIntervalHours = intervalHours,
        supportUrl = "https://old.example/help",
        webPageUrl = "https://old.example/account",
        announce = "Old announcement",
        lastUpdatedMillis = lastUpdated,
    )

    private fun profile(
        id: String,
        name: String,
        address: String,
        uuid: String,
        security: XraySecurity = XraySecurity.Reality("old-cover.example", "old-key", "aa11"),
        transport: XrayTransport = XrayTransport.Raw(),
        addedAtMillis: Long = 10L,
        countryCode: String? = "CA",
        muxEnabled: Boolean = false,
        muxConcurrency: Int = 8,
        fragmentEnabled: Boolean = false,
    ) = XrayVpnProfile(
        id = id,
        name = name,
        serverAddress = address,
        serverPort = 443,
        uuid = uuid,
        security = security,
        transport = transport,
        addedAtMillis = addedAtMillis,
        countryCode = countryCode,
        muxEnabled = muxEnabled,
        muxConcurrency = muxConcurrency,
        fragmentEnabled = fragmentEnabled,
    )

    private fun realityLink(uuid: String, address: String, name: String): String {
        val encodedName = name.replace(" ", "%20")
        return "vless://$uuid@$address:443?security=reality&sni=cover.example&pbk=$PUBLIC_KEY&sid=0011#$encodedName"
    }

    private class FakeFetcher(
        private val result: SubscriptionFetchResult,
    ) : SubscriptionFetcher {
        var calls = 0
            private set

        override suspend fun fetch(url: String): SubscriptionFetchResult {
            calls++
            return result
        }
    }

    private class BlockingFetcher(
        private val result: SubscriptionFetchResult,
        private val started: CompletableDeferred<Unit>,
        private val release: CompletableDeferred<Unit>,
    ) : SubscriptionFetcher {
        var calls = 0
            private set

        override suspend fun fetch(url: String): SubscriptionFetchResult {
            calls++
            started.complete(Unit)
            release.await()
            return result
        }
    }

    private class MemoryStore(initial: List<XraySubscription>) : XraySubscriptionStore {
        private val state = MutableStateFlow(initial)
        private val mutex = Mutex()
        override val subscriptions: Flow<List<XraySubscription>> = state
        val value: List<XraySubscription> get() = state.value
        var updates = 0
            private set

        override suspend fun update(transform: (List<XraySubscription>) -> List<XraySubscription>): List<XraySubscription> =
            mutex.withLock {
                updates++
                transform(state.value).also { state.value = it }
            }
    }

    private companion object {
        const val SUBSCRIPTION_ID = "remote-subscription"
        const val SUBSCRIPTION_URL = "https://provider.example/secret-token"
        const val NOW = 2_000_000_000L
        const val PUBLIC_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        const val UUID_ONE = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
        const val UUID_TWO = "11111111-2222-3333-4444-555555555555"
        const val UUID_THREE = "abcdef01-2345-6789-abcd-ef0123456789"
    }
}
