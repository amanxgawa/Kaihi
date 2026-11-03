package app.kaihi.android.core.vpn

import app.kaihi.android.core.vpn.configfactory.XrayServerConfigFactory
import app.kaihi.android.core.vpn.XrayJson
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayServerConfigFactoryTest {
    @Test
    fun `defaults server inbound listen address to IPv4 wildcard`() {
        val config = parsedConfig()

        assertEquals("0.0.0.0", config.getValue("inbounds").jsonArray.single().jsonObject
            .getValue("listen").jsonPrimitive.content)
    }

    @Test
    fun `dual-stack listen address changes only inbound listen address`() {
        val defaultConfig = parsedConfig()
        val dualStackConfig = parsedConfig(XrayServerConfigFactory.DUAL_STACK_ANY)
        val defaultInbound = defaultConfig.getValue("inbounds").jsonArray.single().jsonObject
        val dualStackInbound = dualStackConfig.getValue("inbounds").jsonArray.single().jsonObject

        assertEquals("::", dualStackInbound.getValue("listen").jsonPrimitive.content)
        assertEquals(
            defaultInbound.getValue("streamSettings").jsonObject.getValue("realitySettings"),
            dualStackInbound.getValue("streamSettings").jsonObject.getValue("realitySettings"),
        )
    }

    @Test
    fun `creates a REALITY server config with resolved secrets`() {
        val config = XrayServerConfigFactory.build(
            uuid = "123e4567-e89b-12d3-a456-426614174000",
            privateKey = "server-private-key",
            shortId = "abcd1234",
            realityServerName = "www.cloudflare.com"
        ).toJson()

        assertTrue(config.contains("\"method\":\"xhttp\""))
        assertTrue(config.contains("\"target\":\"www.cloudflare.com:443\""))
        assertTrue(config.contains("\"privateKey\":\"server-private-key\""))
        assertTrue(config.contains("\"id\":\"123e4567-e89b-12d3-a456-426614174000\""))
        assertTrue(config.contains("\"shortIds\":[\"abcd1234\"]"))
        assertTrue(config.contains("\"decryption\":\"none\""))
    }

    /**
     * REALITY's target is the site being impersonated, and that site listens on 443 whatever port
     * the inbound happens to use. Deriving it from serverPort pointed the handshake at a port the
     * masking host has nothing on, which fails only once traffic is actually flowing.
     */
    @Test
    fun `the masking target stays on 443 when the inbound listens elsewhere`() {
        val config = XrayServerConfigFactory.build(
            uuid = "123e4567-e89b-12d3-a456-426614174000",
            privateKey = "server-private-key",
            shortId = "abcd1234",
            serverPort = 8443,
            realityServerName = "www.cloudflare.com",
        ).toJson()

        assertTrue("inbound must honour the requested port", config.contains("\"port\":8443"))
        assertTrue(
            "REALITY dest must stay on the masking site's own port: $config",
            config.contains("\"target\":\"www.cloudflare.com:443\""),
        )
    }

    private fun parsedConfig(listenAddress: String = XrayServerConfigFactory.IPV4_ANY) =
        XrayJson.default.parseToJsonElement(
            XrayServerConfigFactory.build(
                uuid = "123e4567-e89b-12d3-a456-426614174000",
                privateKey = "server-private-key",
                shortId = "abcd1234",
                realityServerName = "www.cloudflare.com",
                listenAddress = listenAddress,
            ).toJson(),
        ).jsonObject
}
