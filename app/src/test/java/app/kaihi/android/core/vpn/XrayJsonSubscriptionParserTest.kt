package app.kaihi.android.core.vpn

import app.kaihi.android.core.vpn.spec.XhttpMode
import app.kaihi.android.core.vpn.spec.XrayFingerprint
import app.kaihi.android.core.vpn.spec.XrayFingerprintSetting
import app.kaihi.android.core.vpn.spec.XrayFlow
import app.kaihi.android.core.vpn.spec.XrayProtocol
import app.kaihi.android.core.vpn.spec.XraySecurity
import app.kaihi.android.core.vpn.spec.XrayTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayJsonSubscriptionParserTest {

    @Test
    fun `parses Remnawave JSON subscription and keeps supported endpoints in order`() {
        val result = requireNotNull(parseXrayJsonSubscription(REMNAWAVE_FIXTURE, 123L))

        assertEquals(2, result.profiles.size)
        assertEquals(1, result.unsupported)
        assertEquals(0, result.invalid)

        val first = result.profiles[0]
        assertEquals(cleanDisplayText("🇳🇱  Нидерланды", 64), first.name)
        assertEquals(2, first.endpoints.size)
        first.endpoints.forEachIndexed { index, endpoint ->
            assertEquals(listOf("nl1.example.com", "nl2.example.com")[index], endpoint.address)
            assertEquals(XrayProtocol.VLESS, endpoint.protocol)
            assertEquals(443, endpoint.port)
            assertEquals(UUID, endpoint.credential)
            assertEquals(XrayTransport.Raw(XrayFlow.VISION), endpoint.transport)
            assertEquals(
                XraySecurity.Reality(
                    serverName = "nl.example.org",
                    publicKey = K1,
                    shortId = "ab12",
                    fingerprint = XrayFingerprintSetting.Auto(XrayFingerprint.FIREFOX),
                ),
                endpoint.security,
            )
        }

        val second = result.profiles[1]
        assertEquals("🇳🇱 Нидерланды | XHTTP", second.name)
        assertEquals(1, second.endpoints.size)
        assertEquals("/", (second.endpoints.single().transport as XrayTransport.Xhttp).path)
        assertEquals(XhttpMode.AUTO, (second.endpoints.single().transport as XrayTransport.Xhttp).mode)
        assertEquals("", (second.endpoints.single().security as XraySecurity.Reality).shortId)
    }

    @Test
    fun `maps Trojan WebSocket and gRPC transport settings`() {
        val trojan = parseXrayJsonSubscription(
            """[{"outbounds":[{"protocol":"trojan","settings":{"servers":[{"address":"t.example.com","port":8443,"password":"p@ssword"}]},"streamSettings":{"network":"ws","wsSettings":{"path":"/ws","headers":{"Host":"cdn.example.com"}},"security":"tls","tlsSettings":{"serverName":"t.example.com"}}}]}]""",
            0L,
        )!!.profiles.single().endpoints.single()
        assertEquals(XrayProtocol.TROJAN, trojan.protocol)
        assertEquals("p@ssword", trojan.credential)
        assertEquals(XrayTransport.WebSocket("/ws", "cdn.example.com"), trojan.transport)
        assertEquals(XraySecurity.Tls("t.example.com"), trojan.security)

        val grpcOutbound = """{"protocol":"vless","settings":{"vnext":[{"address":"g.example.com","port":443,"users":[{"id":"$UUID","encryption":"none"}]}]},"streamSettings":{"network":"grpc","grpcSettings":{"serviceName":"svc","multiMode":true},"security":"reality","realitySettings":{"serverName":"cover.example","publicKey":"$K1"}}}"""
        val grpc = parseXrayJsonSubscription(subscription(grpcOutbound), 0L)!!.profiles.single().endpoints.single()
        assertEquals(XrayTransport.Grpc("svc", true, null), grpc.transport)
        assertTrue(grpc.security is XraySecurity.Reality)
    }

    @Test
    fun `rejects Trojan password whitespace with the shared share-link validation`() {
        val body = """[{"outbounds":[{"protocol":"trojan","settings":{"servers":[{"address":"t.example.com","port":8443,"password":"p@ss word"}]}}]}]"""

        val result = parseXrayJsonSubscription(body, 0L)!!

        assertTrue(result.profiles.isEmpty())
        assertEquals(1, result.invalid)
    }

    @Test
    fun `returns null for non-subscription and malformed or deeply nested JSON`() {
        listOf(
            "vless://example",
            "{}",
            "[1,2",
            "[".repeat(100) + "]".repeat(100),
        ).forEach { body -> assertNull(parseXrayJsonSubscription(body, 0L)) }
    }

    @Test
    fun `accepts empty arrays and counts non-object elements as invalid`() {
        val empty = parseXrayJsonSubscription("[]", 0L)!!
        assertTrue(empty.profiles.isEmpty())
        assertEquals(0, empty.unsupported)
        assertEquals(0, empty.invalid)

        val mixed = parseXrayJsonSubscription("[1, \"x\"]", 0L)!!
        assertEquals(2, mixed.invalid)
    }

    @Test
    fun `counts string ports malformed hosts and ambiguous server arrays as invalid or unsupported`() {
        val stringPort = parseXrayJsonSubscription(
            subscription(vlessOutbound(port = "\"443\"")),
            0L,
        )!!
        assertEquals(1, stringPort.invalid)

        val malformedHost = parseXrayJsonSubscription(
            subscription(vlessOutbound(address = "bad host!")),
            0L,
        )!!
        assertEquals(1, malformedHost.invalid)

        val twoServers = """{"protocol":"vless","settings":{"vnext":[{"address":"nl1.example.com","port":443,"users":[{"id":"$UUID","encryption":"none"}]},{"address":"nl2.example.com","port":443,"users":[{"id":"$UUID","encryption":"none"}]}]}}"""
        val ambiguous = parseXrayJsonSubscription(subscription(twoServers), 0L)!!
        assertEquals(1, ambiguous.unsupported)
    }

    @Test
    fun `skips outbound proxy settings and dialer proxy chains as unsupported`() {
        val proxySettings = vlessOutbound().replace(
            "\"protocol\":\"vless\"",
            "\"protocol\":\"vless\",\"proxySettings\":{}",
        )
        val dialerProxy = vlessOutbound().replace(
            "\"network\":\"tcp\"",
            "\"sockopt\":{\"dialerProxy\":\"chain\"},\"network\":\"tcp\"",
        )

        listOf(proxySettings, dialerProxy).forEach { outbound ->
            val result = parseXrayJsonSubscription(subscription(outbound), 0L)!!
            assertEquals(1, result.unsupported)
        }
    }

    @Test
    fun `caps endpoints and removes duplicate servers`() {
        val many = (0 until 20).joinToString(",") { vlessOutbound(address = "node$it.example.com") }
        val capped = parseXrayJsonSubscription(subscription(many), 0L)!!.profiles.single()
        assertEquals(XrayVpnProfile.MAX_ENDPOINTS, capped.endpoints.size)

        val duplicate = vlessOutbound()
        val deduplicated = parseXrayJsonSubscription(subscription("$duplicate,$duplicate"), 0L)!!
        assertEquals(1, deduplicated.profiles.single().endpoints.size)
    }

    @Test
    fun `considers at most 256 elements and falls back to a generated nickname`() {
        val cappedBody = "[" + List(300) { "{\"outbounds\":[]}" }.joinToString(",") + "]"
        val capped = parseXrayJsonSubscription(cappedBody, 0L)!!
        assertEquals(256, capped.profiles.size + capped.unsupported + capped.invalid)

        val named = parseXrayJsonSubscription(subscription(vlessOutbound()), 0L)!!.profiles.single()
        assertTrue(named.name.isNotEmpty())
        assertEquals(generateNickname("nl1.example.com"), named.name)
    }

    @Test
    fun `subscription diagnostics redact profile contents`() {
        val result = parseXrayJsonSubscription(REMNAWAVE_FIXTURE, 0L)!!

        assertFalse(result.toString().contains(UUID))
        assertFalse(result.toString().contains(K1))
        assertFalse(result.toString().contains("nl1.example.com"))
    }

    private fun subscription(outbounds: String): String = """[{"outbounds":[$outbounds]}]"""

    private fun vlessOutbound(
        address: String = "nl1.example.com",
        port: String = "443",
        encryption: String = "\"none\"",
        network: String = "tcp",
        transportSettings: String = "\"tcpSettings\":{}",
        securitySettings: String = "\"security\":\"reality\",\"realitySettings\":{\"serverName\":\"nl.example.org\",\"publicKey\":\"$K1\",\"shortId\":\"ab12\",\"fingerprint\":\"firefox\"}",
    ): String = """{"protocol":"vless","settings":{"vnext":[{"address":"$address","port":$port,"users":[{"id":"$UUID","encryption":$encryption,"flow":"xtls-rprx-vision"}]}]},"streamSettings":{"network":"$network",$transportSettings,$securitySettings}}"""

    private companion object {
        const val UUID = "0f1e2d3c-4b5a-4987-8a6b-5c4d3e2f1a0b"
        const val K1 = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"
        const val K2 = "ICEiIyQlJicoKSorLC0uLzAxMjM0NTY3ODk6Ozw9Pj8"

        val REMNAWAVE_FIXTURE = """
            [
              {
                "remarks": "🇳🇱  Нидерланды",
                "inbounds": [{"tag":"socks","port":10808,"listen":"127.0.0.1","protocol":"socks","settings":{"udp":true,"auth":"noauth"}}],
                "outbounds": [
                  {"tag":"proxy-nl","protocol":"vless","settings":{"vnext":[{"address":"nl1.example.com","port":443,"users":[{"id":"$UUID","encryption":"none","flow":"xtls-rprx-vision"}]}]},
                   "streamSettings":{"network":"tcp","tcpSettings":{},"security":"reality","realitySettings":{"serverName":"nl.example.org","publicKey":"$K1","shortId":"ab12","fingerprint":"firefox"}},
                   "finalmask":{"tcp":[{"type":"fragment","settings":{"length":"10-20","packets":"tlshello","interval":"10-20"}}]}},
                  {"tag":"proxy-nl-2","protocol":"vless","settings":{"vnext":[{"address":"nl2.example.com","port":443,"users":[{"id":"$UUID","encryption":"none","flow":"xtls-rprx-vision"}]}]},
                   "streamSettings":{"network":"tcp","tcpSettings":{},"security":"reality","realitySettings":{"serverName":"nl.example.org","publicKey":"$K1","shortId":"ab12","fingerprint":"firefox"}}},
                  {"tag":"frag","protocol":"freedom","settings":{},"streamSettings":{"sockopt":{}}},
                  {"tag":"direct","protocol":"freedom"},
                  {"tag":"block","protocol":"blackhole"}
                ],
                "routing": {"balancers":[{"tag":"balancer_nl","selector":["proxy-nl"],"strategy":{"type":"leastLoad"}}]},
                "burstObservatory": {"subjectSelector":["proxy-nl"]}
              },
              {
                "remarks": "🇳🇱 Нидерланды | ENC",
                "outbounds": [
                  {"tag":"proxy-nl","protocol":"vless","settings":{"vnext":[{"address":"nl1.example.com","port":443,"users":[{"id":"$UUID","encryption":"mlkem768x25519plus.native.0rtt.abc","flow":"xtls-rprx-vision"}]}]},
                   "streamSettings":{"network":"tcp","security":"reality","realitySettings":{"serverName":"enc.example.org","publicKey":"$K2","shortId":"ab12","fingerprint":"firefox"}}}
                ]
              },
              {
                "remarks": "🇳🇱 Нидерланды | XHTTP",
                "outbounds": [
                  {"tag":"proxy-nl","protocol":"vless","settings":{"vnext":[{"address":"nl3.example.com","port":443,"users":[{"id":"$UUID","encryption":"none","flow":""}]}]},
                   "streamSettings":{"network":"xhttp","xhttpSettings":{"mode":"auto","host":"","path":"/"},"security":"reality","realitySettings":{"serverName":"xh.example.org","publicKey":"$K2","shortId":"","fingerprint":"firefox"}}}
                ]
              }
            ]
        """.trimIndent()
    }
}
