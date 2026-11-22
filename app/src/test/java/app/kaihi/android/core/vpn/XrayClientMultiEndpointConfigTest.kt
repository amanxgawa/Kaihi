package app.kaihi.android.core.vpn

import app.kaihi.android.core.vpn.configfactory.XrayClientConfigFactory
import app.kaihi.android.core.vpn.spec.XrayFlow
import app.kaihi.android.core.vpn.spec.XrayProtocol
import app.kaihi.android.core.vpn.spec.XraySecurity
import app.kaihi.android.core.vpn.spec.XrayTransport
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class XrayClientMultiEndpointConfigTest {
    @Test
    fun `mixed endpoints have ordered unique proxy tags and endpoint settings`() {
        val root = config(profile())
        val outbounds = root.getValue("outbounds").jsonArray.map { it.jsonObject }

        assertEquals(
            listOf("proxy", "proxy-2", "proxy-3", "direct", "block", "dns-out"),
            outbounds.map { it.getValue("tag").jsonPrimitive.content },
        )
        val expected = listOf(
            Triple("vless", "nl1.example.com", 443),
            Triple("vless", "nl2.example.com", 443),
            Triple("trojan", "nl3.example.com", 8443),
        )
        expected.forEachIndexed { index, (protocol, address, port) ->
            val outbound = outbounds[index]
            assertEquals(protocol, outbound.getValue("protocol").jsonPrimitive.content)
            val key = if (protocol == "vless") "vnext" else "servers"
            val server = outbound.getValue("settings").jsonObject.getValue(key).jsonArray.single().jsonObject
            assertEquals(address, server.getValue("address").jsonPrimitive.content)
            assertEquals(port, server.getValue("port").jsonPrimitive.content.toInt())
        }
        val visionUser = outbounds[0].getValue("settings").jsonObject.getValue("vnext").jsonArray
            .single().jsonObject.getValue("users").jsonArray.single().jsonObject
        assertEquals("xtls-rprx-vision", visionUser.getValue("flow").jsonPrimitive.content)
        val mux = outbounds[0].getValue("mux").jsonObject
        assertEquals("true", mux.getValue("enabled").jsonPrimitive.content)
        assertEquals(-1, mux.getValue("concurrency").jsonPrimitive.content.toInt())
        assertEquals(8, mux.getValue("xudpConcurrency").jsonPrimitive.content.toInt())
        assertEquals("reject", mux.getValue("xudpProxyUDP443").jsonPrimitive.content)
        assertNull(outbounds[1]["mux"])
        assertNull(outbounds[2]["mux"])

        val balancer = root.getValue("routing").jsonObject.getValue("balancers").jsonArray.single().jsonObject
        assertEquals("balancer_proxy", balancer.getValue("tag").jsonPrimitive.content)
        assertEquals(listOf("proxy"), balancer.getValue("selector").jsonArray.map { it.jsonPrimitive.content })
        val observatory = root.getValue("burstObservatory").jsonObject
        assertEquals(listOf("proxy"), observatory.getValue("subjectSelector").jsonArray.map { it.jsonPrimitive.content })

        val socks = root.getValue("inbounds").jsonArray.map { it.jsonObject }
            .singleOrNull { it["protocol"]?.jsonPrimitive?.content == "socks" }
        assertNotNull("SOCKS inbound is missing", socks)
        assertEquals("password", socks!!.getValue("settings").jsonObject.getValue("auth").jsonPrimitive.content)
        val blackhole = root.getValue("routing").jsonObject.getValue("rules").jsonArray.map { it.jsonObject }
            .singleOrNull {
                it["outboundTag"]?.jsonPrimitive?.content == "block" &&
                    it["network"]?.jsonPrimitive?.content == "udp" &&
                    it["port"]?.jsonPrimitive?.content == "443"
            }
        assertNotNull("UDP/443 blackhole rule is missing", blackhole)
    }

    @Test
    fun `multiple endpoints use a thirty second probe interval`() {
        assertEquals("30s", config(profile()).probeInterval())
    }

    @Test
    fun `single endpoint keeps a one minute probe interval`() {
        val profile = profile()
        assertEquals("1m", config(profile.copy(endpoints = profile.endpoints.take(1))).probeInterval())
    }

    @Test
    fun `twenty endpoints emit only the first sixteen proxy outbounds`() {
        val endpoints = (1..20).map { testEndpoint(address = "nl$it.example.com") }
        val outbounds = config(profile().copy(endpoints = endpoints))
            .getValue("outbounds").jsonArray.map { it.jsonObject }
        val proxies = outbounds.filter { it.getValue("tag").jsonPrimitive.content.startsWith("proxy") }

        assertEquals(16, proxies.size)
        assertEquals(
            listOf("proxy") + (2..16).map { "proxy-$it" },
            proxies.map { it.getValue("tag").jsonPrimitive.content },
        )
        assertEquals(
            (1..16).map { "nl$it.example.com" },
            proxies.map {
                it.getValue("settings").jsonObject.getValue("vnext").jsonArray.single().jsonObject
                    .getValue("address").jsonPrimitive.content
            },
        )
    }

    @Test
    fun `empty endpoints fail with a credential free message`() {
        val profile = profile().copy(endpoints = emptyList())

        val exception = assertThrows(IllegalArgumentException::class.java) {
            XrayClientConfigFactory.build(profile)
        }

        assertEquals("profile has no endpoints", exception.message)
    }

    private fun profile() = XrayVpnProfile(
        id = "multi-endpoint",
        name = "test",
        addedAtMillis = 0L,
        muxEnabled = true,
        muxConcurrency = 8,
        endpoints = listOf(
            testEndpoint(address = "nl1.example.com", transport = XrayTransport.Raw(XrayFlow.VISION)),
            testEndpoint(address = "nl2.example.com", transport = XrayTransport.Xhttp()),
            testEndpoint(
                address = "nl3.example.com",
                port = 8443,
                protocol = XrayProtocol.TROJAN,
                credential = "trojan-password",
                security = XraySecurity.Tls("nl3.example.com"),
                transport = XrayTransport.WebSocket("/ws"),
            ),
        ),
    )

    private fun config(profile: XrayVpnProfile): JsonObject =
        Json.parseToJsonElement(XrayClientConfigFactory.build(profile).toJson()).jsonObject

    private fun JsonObject.probeInterval(): String = getValue("burstObservatory").jsonObject
        .getValue("pingConfig").jsonObject.getValue("interval").jsonPrimitive.content
}
