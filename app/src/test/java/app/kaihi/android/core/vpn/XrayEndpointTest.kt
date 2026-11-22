package app.kaihi.android.core.vpn

import app.kaihi.android.core.vpn.spec.XrayFingerprint
import app.kaihi.android.core.vpn.spec.XrayFingerprintSetting
import app.kaihi.android.core.vpn.spec.XraySecurity
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayEndpointTest {
    @Test
    fun `profile with multiple endpoints round trips in the new wire shape`() {
        val profile = profile(
            endpoints = listOf(
                testEndpoint(address = "198.51.100.1"),
                testEndpoint(address = "198.51.100.2", port = 8443),
            ),
        )

        val encoded = XrayJson.default.encodeToString(XrayVpnProfile.serializer(), profile)
        val decoded = XrayJson.default.decodeFromString(XrayVpnProfile.serializer(), encoded)

        assertEquals(profile, decoded)
        assertTrue(encoded.contains("\"endpoints\""))
        assertFalse(encoded.contains("\"serverAddress\""))
        assertFalse(encoded.contains("\"uuid\""))
    }

    @Test
    fun `endpoint and profile string representations redact credentials`() {
        val endpoint = testEndpoint(credential = "p@ss word")
        val profile = profile(endpoints = listOf(endpoint))

        assertFalse(endpoint.toString().contains("p@ss word"))
        assertFalse(profile.toString().contains("p@ss word"))
    }

    @Test
    fun `fingerprint helpers read the first reality setting and update every reality endpoint`() {
        val first = XrayFingerprintSetting.Fixed(XrayFingerprint.FIREFOX)
        val third = XrayFingerprintSetting.Auto(XrayFingerprint.SAFARI)
        val profile = profile(
            endpoints = listOf(
                testEndpoint(security = reality(first)),
                testEndpoint(security = XraySecurity.Tls("tls.example")),
                testEndpoint(security = reality(third)),
            ),
        )

        val transformed = profile.withRealityFingerprint { XrayFingerprintSetting.Fixed(XrayFingerprint.CHROME) }

        assertEquals(first, profile.realityFingerprint)
        assertEquals(XrayFingerprintSetting.Fixed(XrayFingerprint.CHROME), transformed.endpoints[0].realityFingerprint())
        assertEquals(profile.endpoints[1], transformed.endpoints[1])
        assertEquals(XrayFingerprintSetting.Fixed(XrayFingerprint.CHROME), transformed.endpoints[2].realityFingerprint())
        assertEquals(null, profile(endpoints = listOf(testEndpoint(security = XraySecurity.Tls("tls.example")))).realityFingerprint)
    }

    private fun profile(endpoints: List<XrayEndpoint>) = XrayVpnProfile(
        id = "profile-id",
        name = "Test server",
        endpoints = endpoints,
        addedAtMillis = 1L,
    )

    private fun reality(fingerprint: XrayFingerprintSetting) = XraySecurity.Reality(
        serverName = "cover.example",
        publicKey = "public-key",
        shortId = "abcd",
        fingerprint = fingerprint,
    )

    private fun XrayEndpoint.realityFingerprint(): XrayFingerprintSetting? =
        (security as? XraySecurity.Reality)?.fingerprint
}
