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
            serverAddress = "203.0.113.1",
            serverPort = 443,
            credential = "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
            security = XraySecurity.Reality(
                serverName = realityServerName,
                publicKey = "publicKeyValue",
                shortId = "shortIdValue",
            ),
            addedAtMillis = 0L,
        )

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
    fun `exports the preferred concrete fingerprint for auto and fixed settings`() {
        val base = profile()
        val reality = base.security as XraySecurity.Reality
        val auto = base.copy(
            security = reality.copy(
                fingerprint = XrayFingerprintSetting.Auto(),
            ),
        )
        val fixed = base.copy(
            security = reality.copy(
                fingerprint = XrayFingerprintSetting.Fixed(XrayFingerprint.SAFARI),
            ),
        )

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
            serverAddress = "example.com",
            serverPort = 443,
            protocol = XrayProtocol.TROJAN,
            credential = "p@ss word",
            security = XraySecurity.Reality(
                serverName = "example.com",
                publicKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                shortId = "ab12",
                fingerprint = XrayFingerprintSetting.Auto(XrayFingerprint.FIREFOX),
            ),
            transport = XrayTransport.Raw(),
            addedAtMillis = 10L,
        )
        val uri = original.toShareUri()

        assertTrue(uri.startsWith("trojan://p%40ss%20word@example.com:443?security=reality"))
        assertFalse(uri.contains("encryption="))
        assertFalse(uri.contains("flow="))

        val validPasswordProfile = original.copy(credential = "p@ssword")
        val parsed = (parseShareLink(validPasswordProfile.toShareUri(), 11L) as ShareLinkResult.Success).profile
        assertEquals(validPasswordProfile.copy(id = parsed.id, addedAtMillis = parsed.addedAtMillis), parsed)
    }
}
