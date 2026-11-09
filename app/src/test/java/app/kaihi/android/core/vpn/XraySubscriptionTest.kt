package app.kaihi.android.core.vpn

import app.kaihi.android.core.vpn.spec.XraySecurity
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XraySubscriptionTest {

    private val server = XrayVpnProfile(
        id = "server-1",
        name = "Imported server",
        serverAddress = "server.example",
        serverPort = 443,
        uuid = "123e4567-e89b-12d3-a456-426614174000",
        security = XraySecurity.Tls(serverName = "server.example"),
        addedAtMillis = 1_700_000_000_000L,
    )

    @Test
    fun `subscription sources and remote user info round trip`() {
        val subscriptions = listOf(
            XraySubscription(id = "deployed", name = "Deployed", servers = listOf(server)),
            XraySubscription(
                id = XraySubscription.IMPORTED_ID,
                name = "ignored",
                servers = listOf(server.copy(id = "imported")),
                source = XraySubscriptionSource.Imported,
            ),
            XraySubscription(
                id = "remote-id",
                name = "Panel",
                servers = listOf(server.copy(id = "remote")),
                source = XraySubscriptionSource.Remote(
                    url = "https://panel.example/sub/SECRETTOKEN",
                    title = "Personal panel",
                    userInfo = XraySubscriptionUserInfo(
                        uploadBytes = 1_000L,
                        downloadBytes = 2_000L,
                        totalBytes = 10_000L,
                        expireEpochSeconds = 1_800_000_000L,
                    ),
                    updateIntervalHours = 12,
                    supportUrl = "https://panel.example/support",
                    webPageUrl = "https://panel.example/",
                    announce = "Updated",
                    lastUpdatedMillis = 1_700_000_000_000L,
                ),
            ),
        )

        val encoded = XrayJson.default.encodeToString(ListSerializer(XraySubscription.serializer()), subscriptions)
        val decoded = XrayJson.default.decodeFromString(ListSerializer(XraySubscription.serializer()), encoded)

        assertEquals(subscriptions, decoded)
        assertTrue(encoded, encoded.contains("\"type\":\"imported\""))
        assertTrue(encoded, encoded.contains("\"type\":\"remote\""))
    }

    @Test
    fun `remote source and containing subscription do not reveal the subscription url`() {
        val source = XraySubscriptionSource.Remote(url = "https://panel.example/sub/SECRETTOKEN")
        val subscription = XraySubscription("remote", "Panel", emptyList(), source)

        assertFalse(source.toString().contains("SECRETTOKEN"))
        assertFalse(subscription.toString().contains("SECRETTOKEN"))
    }

    @Test
    fun `ssh access does not reveal the expected fingerprint`() {
        val access = XraySshAccess("server.example", 22, "root", "SHA256:SECRET")

        assertFalse(access.toString().contains("SHA256:SECRET"))
        assertTrue(access.toString().contains("server.example"))
    }
}
