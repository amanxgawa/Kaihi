package app.kaihi.android.core.vpn

import app.kaihi.android.core.vpn.spec.XrayFingerprint
import app.kaihi.android.core.vpn.spec.XrayFingerprintSetting
import app.kaihi.android.core.vpn.spec.XrayProtocol
import app.kaihi.android.core.vpn.spec.XraySecurity
import app.kaihi.android.core.vpn.spec.XrayTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareUriTest {

    private fun profile(name: String = "My Server", realityServerName: String = "www.example.com") =
        XrayVpnProfile(
            id = "server-1",
            name = name,
            endpoints = listOf(
                testEndpoint(
                    address = "203.0.113.1",
                    credential = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                    security = XraySecurity.Reality(
                        serverName = realityServerName,
                        publicKey = "publicKeyValue",
                        shortId = "shortIdValue",
                    ),
                ),
            ),
            addedAtMillis = 0L,
        )

    @Test
    fun `multiple endpoints export separate links with the row name and round trip`() {
        val a = testEndpoint(
            address = "a.example.com",
            credential = "11111111-1111-4111-8111-111111111111",
            security = XraySecurity.Reality(
                serverName = "cover.example",
                publicKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                shortId = "0011",
                fingerprint = XrayFingerprintSetting.Auto(XrayFingerprint.FIREFOX),
            ),
            transport = XrayTransport.Raw(),
        )
        val c = a.copy(address = "c.example.com")
        val original = profile(name = "NL").copy(endpoints = listOf(a, c))

        val lines = original.toShareUri().lines()

        assertEquals(2, lines.size)
        val parsed = lines.map { (parseShareLink(it, 1L) as ShareLinkResult.Success).profile }
        assertEquals(listOf(listOf(a), listOf(c)), parsed.map { it.endpoints })
        assertEquals(listOf("NL", "NL"), parsed.map { it.name })
    }

    @Test
    fun `includes host, port, and uuid unencoded`() {
        val uri = profile().toShareUri()

        assertTrue(uri.startsWith("vless://a1b2c3d4-e5f6-7890-abcd-ef1234567890@203.0.113.1:443?"))
    }

    @Test
    fun `carries reality parameters clients expect`() {
        val uri = profile().toShareUri()

        assertTrue(uri.contains("security=reality"))
        assertTrue(uri.contains("sni=www.example.com"))
        assertTrue(uri.contains("pbk=publicKeyValue"))
        assertTrue(uri.contains("sid=shortIdValue"))
        assertTrue(uri.contains("type=xhttp"))
    }

    @Test
    fun `REALITY with the IP address as SNI omits sni and round trips the endpoint`() {
        val endpoint = testEndpoint(
            address = "203.0.113.7",
            credential = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
            security = XraySecurity.Reality(
                serverName = "203.0.113.7",
                publicKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                shortId = "0123456789abcdef",
                fingerprint = XrayFingerprintSetting.Auto(XrayFingerprint.CHROME),
            ),
            transport = XrayTransport.Grpc("abcd-efgh-ijkl-m", false, null),
        )
        val uri = endpoint.toShareUri("x")
        val parsed = parseShareLink(uri, 0L)

        assertEquals(endpoint, (parsed as ShareLinkResult.Success).profile.firstEndpoint)
        assertFalse(uri.contains("sni="))
    }

    @Test
    fun `exports the preferred concrete fingerprint for auto and fixed settings`() {
        val base = profile()
        val auto = base.copyEndpoints {
            it.copy(security = (it.security as XraySecurity.Reality).copy(
                fingerprint = XrayFingerprintSetting.Auto(),
            ))
        }
        val fixed = base.copyEndpoints {
            it.copy(security = (it.security as XraySecurity.Reality).copy(
                fingerprint = XrayFingerprintSetting.Fixed(XrayFingerprint.SAFARI),
            ))
        }

        assertTrue(auto.toShareUri().contains("&fp=firefox"))
        assertTrue(fixed.toShareUri().contains("&fp=safari"))
        assertTrue(!auto.toShareUri().contains("&fp=auto"))
    }

    @Test
    fun `sets mode and host explicitly rather than relying on an importing client's own defaults`() {
        // Regression test: an omitted mode left it to each importing client's own guess at
        // xray-core's REALITY-aware auto-resolution, which is not guaranteed to match. A
        // "context deadline exceeded" against a live server traced back to exactly this gap.
        val uri = profile().toShareUri()

        assertTrue(uri.contains("mode=stream-one"))
        assertTrue(uri.contains("host=www.example.com"))
    }

    @Test
    fun `percent-encodes the remark instead of using form-encoding plus signs`() {
        val uri = profile(name = "My Server").toShareUri()

        assertTrue(uri.endsWith("#My%20Server"))
    }

    @Test
    fun `percent-encodes a query value containing reserved characters`() {
        val uri = profile(realityServerName = "a b&c").toShareUri()

        assertEquals(false, uri.contains("sni=a b&c"))
        assertTrue(uri.contains("sni=a%20b%26c"))
    }

    @Test
    fun `Trojan share URI encodes the password and round trips without VLESS options`() {
        val original = XrayVpnProfile(
            id = "trojan-1",
            name = "Trojan node",
            endpoints = listOf(
                testEndpoint(
                    address = "example.com",
                    protocol = XrayProtocol.TROJAN,
                    credential = "p@ss word",
                    security = XraySecurity.Reality(
                        serverName = "example.com",
                        publicKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                        shortId = "ab12",
                        fingerprint = XrayFingerprintSetting.Auto(XrayFingerprint.FIREFOX),
                    ),
                    transport = XrayTransport.Raw(),
                ),
            ),
            addedAtMillis = 10L,
        )
        val uri = original.toShareUri()

        assertTrue(uri.startsWith("trojan://p%40ss%20word@example.com:443?security=reality"))
        assertFalse(uri.contains("encryption="))
        assertFalse(uri.contains("flow="))

        val validPasswordProfile = original.copyEndpoints { it.copy(credential = "p@ssword") }
        val parsed = (parseShareLink(validPasswordProfile.toShareUri(), 11L) as ShareLinkResult.Success).profile
        assertEquals(validPasswordProfile.copy(id = parsed.id, addedAtMillis = parsed.addedAtMillis), parsed)
    }

    @Test
    fun `WebSocket exports an encoded path and optional host and round trips`() {
        listOf(null, "cdn.example.com").forEach { host ->
            val original = profile().copyEndpoints {
                it.copy(
                    security = XraySecurity.Tls("cdn.example.com"),
                    transport = XrayTransport.WebSocket("/ws?ed=2048", host),
                )
            }
            val uri = original.toShareUri()
            val expectedHost = host?.let { "&host=$it" }.orEmpty()

            assertTrue(uri.contains("&type=ws&path=%2Fws%3Fed%3D2048$expectedHost#"))
            assertEquals(original.firstEndpoint.transport, (parseShareLink(uri, 1L) as ShareLinkResult.Success).profile.firstEndpoint.transport)
        }
    }

    @Test
    fun `HTTPUpgrade exports a path and optional host and round trips for Trojan`() {
        listOf(null, "cdn.example.com").forEach { host ->
            val original = profile().copyEndpoints {
                it.copy(
                    protocol = XrayProtocol.TROJAN,
                    credential = "pw",
                    security = XraySecurity.Tls("cdn.example.com"),
                    transport = XrayTransport.HttpUpgrade("/up", host),
                )
            }
            val uri = original.toShareUri()
            val expectedHost = host?.let { "&host=$it" }.orEmpty()

            assertTrue(uri.contains("&type=httpupgrade&path=%2Fup$expectedHost#"))
            assertEquals(original.firstEndpoint.transport, (parseShareLink(uri, 1L) as ShareLinkResult.Success).profile.firstEndpoint.transport)
        }
    }

    @Test
    fun `gRPC exports encoded service name multi mode and authority and round trips`() {
        val original = profile().copyEndpoints {
            it.copy(
                security = XraySecurity.Reality(
                    serverName = "grpc.example.com",
                    publicKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                    shortId = "ab12",
                ),
                transport = XrayTransport.Grpc("gun-svc/method", true, "grpc.example.com"),
            )
        }
        val uri = original.toShareUri()

        assertTrue(uri.contains("&type=grpc&serviceName=gun-svc%2Fmethod&mode=multi&authority=grpc.example.com#"))
        assertEquals(original.firstEndpoint.transport, (parseShareLink(uri, 1L) as ShareLinkResult.Success).profile.firstEndpoint.transport)
    }

    @Test
    fun `gRPC omits empty service name gun mode and absent authority and round trips`() {
        val original = profile().copyEndpoints {
            it.copy(
                security = XraySecurity.Tls("grpc.example.com"),
                transport = XrayTransport.Grpc(),
            )
        }
        val uri = original.toShareUri()

        assertTrue(uri.contains("&type=grpc#"))
        assertFalse(uri.contains("serviceName="))
        assertFalse(uri.contains("mode="))
        assertFalse(uri.contains("authority="))
        assertEquals(original.firstEndpoint.transport, (parseShareLink(uri, 1L) as ShareLinkResult.Success).profile.firstEndpoint.transport)
    }
}
