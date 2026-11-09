package app.kaihi.android.feature.wizard

import app.kaihi.android.core.vpn.XrayJson
import app.kaihi.android.core.vpn.XrayProtectionState
import app.kaihi.android.core.vpn.XrayServerProtection
import app.kaihi.android.core.vpn.XrayVpnProfile
import app.kaihi.android.core.vpn.spec.XraySecurity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XrayServerProtectionSerializationTest {
    @Test
    fun `server protection null and value round trip in profiles`() {
        val value = XrayServerProtection(
            bruteForceGuard = XrayProtectionState.ENABLED,
            securityUpdates = XrayProtectionState.FAILED,
        )

        listOf(null, value).forEach { protection ->
            val original = profile(protection)
            val encoded = XrayJson.default.encodeToString(XrayVpnProfile.serializer(), original)

            assertEquals(original, XrayJson.default.decodeFromString(XrayVpnProfile.serializer(), encoded))
        }
    }

    @Test
    fun `profile without stored protection decodes as unknown`() {
        val encodedWithProtection = XrayJson.default.encodeToString(
            XrayVpnProfile.serializer(),
            profile(XrayServerProtection(XrayProtectionState.OFF, XrayProtectionState.OFF)),
        )
        val storedWithoutField = JsonObject(
            XrayJson.default.parseToJsonElement(encodedWithProtection).jsonObject - "serverProtection",
        ).toString()

        val decoded = XrayJson.default.decodeFromString(XrayVpnProfile.serializer(), storedWithoutField)

        assertNull(decoded.serverProtection)
    }

    private fun profile(protection: XrayServerProtection?) = XrayVpnProfile(
        id = "profile-id",
        name = "Test server",
        serverAddress = "server.example",
        serverPort = 443,
        uuid = "00000000-0000-4000-8000-000000000001",
        security = XraySecurity.Reality(
            serverName = "mask.example",
            publicKey = "public-key",
            shortId = "short-id",
        ),
        addedAtMillis = 1L,
        serverProtection = protection,
    )
}
