package app.kaihi.android.core.vpn

import app.kaihi.android.core.storage.InMemoryAppStorage
import app.kaihi.android.core.storage.StorageDomain
import app.kaihi.android.core.vpn.spec.XraySecurity
import app.kaihi.android.core.vpn.spec.XrayProtocol
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XraySubscriptionStoreTest {

    private val legacyProfile = XrayVpnProfile(
        id = "ignored",
        name = "Silent Harbor",
        ssh = XraySshAccess(
            host = "203.0.113.9",
            port = 22,
            username = "root",
            expectedFingerprint = "SHA256:oldfingerprint",
        ),
        endpoints = listOf(
            testEndpoint(
                address = "203.0.113.9",
                credential = "a1b2c3d4-e5f6-4890-abcd-ef1234567890",
                security = XraySecurity.Reality(serverName = "www.example.com", publicKey = "key", shortId = "ab"),
            ),
        ),
        addedAtMillis = 1_700_000_000_000L,
    )

    private fun TestScope.store(storage: InMemoryAppStorage) =
        XraySubscriptionStore(storage, StandardTestDispatcher(testScheduler))

    @Test
    fun `starts empty when nothing is stored`() = runTest {
        assertEquals(emptyList<XraySubscription>(), store(InMemoryAppStorage()).subscriptions.first())
    }

    @Test
    fun `Trojan profile string representation redacts its credential`() {
        val password = "p@ss word"
        val profile = legacyProfile.copy(
            endpoints = listOf(legacyProfile.endpoints.first().copy(protocol = XrayProtocol.TROJAN, credential = password)),
        )

        assertFalse(profile.toString().contains(password))
    }

    @Test
    fun `stored endpoint without protocol decodes as a VLESS credential`() {
        val encoded = XrayJson.default.encodeToString(XrayVpnProfile.serializer(), legacyProfile)
        val stored = XrayJson.default.parseToJsonElement(encoded).jsonObject.toString()
        assertTrue(stored.contains("\"endpoints\":"))
        assertTrue(stored.contains("\"credential\":"))
        assertFalse(stored.contains("\"uuid\":"))
        assertFalse(stored.contains("\"protocol\":"))

        val decoded = XrayJson.default.decodeFromString(XrayVpnProfile.serializer(), stored)

        assertEquals(XrayProtocol.VLESS, decoded.endpoints.first().protocol)
        assertEquals(legacyProfile.endpoints.first().credential, decoded.endpoints.first().credential)
    }

    @Test
    fun `an update is visible to collectors and survives a fresh store over the same storage`() = runTest {
        val storage = InMemoryAppStorage()
        val store = store(storage)
        val added = XraySubscription(id = "home", name = "Home", servers = listOf(legacyProfile))

        store.update { it + added }

        assertEquals(listOf(added), store.subscriptions.first())
        assertEquals(listOf(added), store(storage).subscriptions.first())
    }

    /**
     * The ciphertext is encrypted under a non-exportable Keystore key that is gone, so there is
     * nothing left to recover from it. Leaving it in place only meant the next write silently
     * overwrote it anyway, one step later and with no record of what happened.
     */
    @Test
    fun `clears profiles it can no longer decrypt instead of leaving them to be overwritten`() = runTest {
        val storage = InMemoryAppStorage(undecryptable = setOf(SUBSCRIPTIONS_KEY))

        val subscriptions = store(storage).subscriptions.first()

        assertEquals(emptyList<XraySubscription>(), subscriptions)
        assertTrue("the dead ciphertext must not be left behind", SUBSCRIPTIONS_KEY in storage.removed)
    }

    @Test
    fun `stays usable after profiles could not be decrypted`() = runTest {
        val storage = InMemoryAppStorage(undecryptable = setOf(SUBSCRIPTIONS_KEY))
        val store = store(storage)
        store.subscriptions.first()
        val added = XraySubscription(id = "home", name = "Home", servers = listOf(legacyProfile))

        store.update { it + added }

        assertEquals(listOf(added), store.subscriptions.first())
    }

    private companion object {
        const val SUBSCRIPTIONS_KEY = "subscriptions"
    }
}
