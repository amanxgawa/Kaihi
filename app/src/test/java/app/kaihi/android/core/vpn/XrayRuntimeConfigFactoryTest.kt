package app.kaihi.android.core.vpn

import app.kaihi.android.core.vpn.configfactory.XrayClientConfigFactory
import app.kaihi.android.core.vpn.spec.XraySecurity
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayRuntimeConfigFactoryTest {
    private fun profile() = XrayVpnProfile(
        id = "server-1",
        name = "test",
        sshHost = "ssh.example",
        sshPort = 22,
        sshUsername = "root",
        serverAddress = "proxy.example",
        serverPort = 443,
        uuid = "123e4567-e89b-12d3-a456-426614174000",
        security = XraySecurity.Reality(
            serverName = "www.example.com",
            publicKey = "public-key",
            shortId = "abcd",
        ),
        deployedAtMillis = 0L,
    )

    @Test
    fun `creates a local SOCKS endpoint`() {
        val config = XrayClientConfigFactory.createRuntimeConfig(profile(), ipv6Enabled = false)

        assertFalse(config.ipv6Enabled)
        assertTrue(config.rawJson.contains("\"protocol\":\"vless\""))
        assertTrue(config.rawJson.contains("proxy.example"))
        assertTrue(config.rawJson.contains("\"method\":\"xhttp\""))
        assertTrue(config.rawJson.contains("\"password\":\"public-key\""))
        assertTrue(config.socksEndpoint.host == "127.0.0.1")
        assertTrue(config.socksEndpoint.port == 10_808)
        // Never noauth. See SocksEndpoint for why an unauthenticated loopback SOCKS proxy is a
        // real local-exploit surface (any other app on the device can reach 127.0.0.1).
        assertTrue(config.socksEndpoint.username != null)
        assertTrue(config.socksEndpoint.password != null)
        assertTrue(config.rawJson.contains("\"auth\":\"password\""))
        assertTrue(config.rawJson.contains(config.socksEndpoint.password!!))
    }

    @Test
    fun `IPv6 enabled runtime config carries its choice and lifts the IPv6 blackhole`() {
        val config = XrayClientConfigFactory.createRuntimeConfig(profile(), ipv6Enabled = true)
        val rules = XrayJson.default.parseToJsonElement(config.rawJson).jsonObject
            .getValue("routing").jsonObject.getValue("rules").jsonArray.map { it.jsonObject }

        assertTrue(config.ipv6Enabled)
        assertEquals(5, rules.size)
        assertFalse(rules.any { it["ip"]?.jsonArray?.any { ip -> ip.jsonPrimitive.content == "::/0" } == true })

        // Restart intents serialize the whole config, preserving the session's original choice.
        val restarted = XrayJson.default.decodeFromString<XrayRuntimeConfig>(XrayJson.default.encodeToString(config))
        assertEquals(config, restarted)
        assertTrue(restarted.ipv6Enabled)
    }

    @Test
    fun `restart payload without an IPv6 choice defaults to disabled`() {
        val payload = """
            {
              "rawJson": "{}",
              "socksEndpoint": {
                "host": "127.0.0.1",
                "port": 10808,
                "username": "kaihi",
                "password": "secret"
              },
              "statusIntervalMillis": 1000,
              "serverId": "server-1"
            }
        """.trimIndent()

        val config = XrayJson.default.decodeFromString<XrayRuntimeConfig>(payload)

        assertFalse(config.ipv6Enabled)
        assertEquals("server-1", config.serverId)
        assertNull(config.settingsKey)
        assertEquals(SocksEndpoint("127.0.0.1", 10_808, "kaihi", "secret"), config.socksEndpoint)
        val reencoded = XrayJson.default.encodeToString(config)
        assertFalse(reencoded.contains("ipv6Enabled"))
        assertFalse(reencoded.contains("settingsKey"))
    }
}
