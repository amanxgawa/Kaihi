package app.kaihi.android.core.vpn

import app.kaihi.android.core.vpn.configfactory.XrayClientConfigFactory
import app.kaihi.android.core.vpn.spec.XrayFingerprint
import app.kaihi.android.core.vpn.spec.XrayFingerprintSetting
import app.kaihi.android.core.vpn.spec.XraySecurity
import app.kaihi.android.core.vpn.spec.candidates
import app.kaihi.android.core.vpn.spec.XrayTransport
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
        serverAddress = "proxy.example",
        serverPort = 443,
        uuid = "123e4567-e89b-12d3-a456-426614174000",
        security = XraySecurity.Reality(
            serverName = "www.example.com",
            publicKey = "public-key",
            shortId = "abcd",
        ),
        addedAtMillis = 0L,
    )

    @Test
    fun `creates a local SOCKS endpoint`() {
        val config = XrayClientConfigFactory.createRuntimeConfig(profile(), settings(ipv6Enabled = false))

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
    fun `auto runtime config carries ordered candidate configs with shared session inputs`() {
        val config = XrayClientConfigFactory.createRuntimeConfig(profile(), settings(ipv6Enabled = false))
        val expectedFingerprints = XrayFingerprintSetting.Auto().candidates()

        assertEquals(expectedFingerprints, config.fingerprintCandidates.map { it.fingerprint })
        assertEquals(config.fingerprintCandidates.first().rawJson, config.rawJson)
        assertEquals(expectedFingerprints.first(), config.fingerprint)

        val roots = config.fingerprintCandidates.map { XrayJson.default.parseToJsonElement(it.rawJson).jsonObject }
        val firstAccounts = roots.first().getValue("inbounds").jsonArray.single().jsonObject
            .getValue("settings").jsonObject.getValue("accounts").jsonArray.single().jsonObject
        val firstProbeUrl = roots.first().getValue("burstObservatory").jsonObject
            .getValue("pingConfig").jsonObject.getValue("destination").jsonPrimitive.content

        roots.zip(config.fingerprintCandidates).forEach { (root, candidate) ->
            val reality = root.outbound("proxy").getValue("streamSettings").jsonObject
                .getValue("realitySettings").jsonObject
            val accounts = root.getValue("inbounds").jsonArray.single().jsonObject
                .getValue("settings").jsonObject.getValue("accounts").jsonArray.single().jsonObject
            val probeUrl = root.getValue("burstObservatory").jsonObject
                .getValue("pingConfig").jsonObject.getValue("destination").jsonPrimitive.content

            assertEquals(candidate.fingerprint.wireValue, reality.getValue("fingerprint").jsonPrimitive.content)
            assertEquals(firstAccounts, accounts)
            assertEquals(firstProbeUrl, probeUrl)
            assertEquals(
                config.fingerprintCandidates.first().rawJson
                    .replace("\"fingerprint\":\"${config.fingerprintCandidates.first().fingerprint.wireValue}\"", "\"fingerprint\":\"normalized\""),
                candidate.rawJson.replace(
                    "\"fingerprint\":\"${candidate.fingerprint.wireValue}\"",
                    "\"fingerprint\":\"normalized\"",
                ),
            )
        }
    }

    @Test
    fun `fixed runtime config does not carry fingerprint candidates`() {
        val fixed = XrayFingerprintSetting.Fixed(XrayFingerprint.SAFARI)
        val config = XrayClientConfigFactory.createRuntimeConfig(
            profile().copy(security = (profile().security as XraySecurity.Reality).copy(fingerprint = fixed)),
            settings = settings(ipv6Enabled = false),
        )

        assertTrue(config.fingerprintCandidates.isEmpty())
        assertEquals(XrayFingerprint.SAFARI, config.fingerprint)
        val reality = XrayJson.default.parseToJsonElement(config.rawJson).jsonObject
            .outbound("proxy").getValue("streamSettings").jsonObject.getValue("realitySettings").jsonObject
        assertEquals("safari", reality.getValue("fingerprint").jsonPrimitive.content)
    }

    @Test
    fun `IPv6 enabled runtime config carries its choice and lifts the IPv6 blackhole`() {
        val config = XrayClientConfigFactory.createRuntimeConfig(profile(), settings(ipv6Enabled = true))
        val rules = XrayJson.default.parseToJsonElement(config.rawJson).jsonObject
            .getValue("routing").jsonObject.getValue("rules").jsonArray.map { it.jsonObject }

        assertTrue(config.ipv6Enabled)
        assertEquals(5, rules.size)
        assertFalse(rules.any { it["ip"]?.jsonArray?.any { ip -> ip.jsonPrimitive.content == "::/0" } == true })

        // Restart intents serialize the whole config, preserving the session's original choice.
        val restarted = XrayJson.default.decodeFromString<XrayRuntimeConfig>(XrayJson.default.encodeToString(config))
        assertEquals(config, restarted)
        assertEquals(config.fingerprintCandidates, restarted.fingerprintCandidates)
        assertTrue(restarted.ipv6Enabled)
    }

    @Test
    fun `normal mode resolves advanced settings to defaults without changing the saved profile`() {
        val savedProfile = profile().copy(
            transport = XrayTransport.Raw(),
            muxEnabled = true,
            fragmentEnabled = true,
            security = (profile().security as XraySecurity.Reality).copy(
                fingerprint = XrayFingerprintSetting.Fixed(XrayFingerprint.CHROME),
            ),
        )
        val config = XrayClientConfigFactory.createRuntimeConfig(
            savedProfile,
            TunnelSettings(advancedMode = false, ipv6Enabled = true),
        )
        val defaultConfig = XrayClientConfigFactory.createRuntimeConfig(
            profile().copy(transport = XrayTransport.Raw()),
            TunnelSettings(advancedMode = false, ipv6Enabled = false),
        )
        val root = XrayJson.default.parseToJsonElement(config.rawJson).jsonObject
        val rules = root.getValue("routing").jsonObject.getValue("rules").jsonArray.map { it.jsonObject }
        val stream = root.outbound("proxy").getValue("streamSettings").jsonObject
        val reality = stream.getValue("realitySettings").jsonObject

        assertEquals(normalizeVolatileValues(defaultConfig.rawJson), normalizeVolatileValues(config.rawJson))
        assertFalse(config.ipv6Enabled)
        assertTrue(rules.any { rule -> rule["ip"]?.jsonArray?.any { it.jsonPrimitive.content == "::/0" } == true })
        assertNull(root.outbound("proxy")["mux"])
        assertNull(stream["finalmask"])
        assertEquals("firefox", reality.getValue("fingerprint").jsonPrimitive.content)
        assertTrue(config.fingerprintCandidates.isNotEmpty())
        assertEquals(
            XrayFingerprintSetting.Fixed(XrayFingerprint.CHROME),
            (savedProfile.security as XraySecurity.Reality).fingerprint,
        )
        assertTrue(savedProfile.muxEnabled)
        assertTrue(savedProfile.fragmentEnabled)
    }

    @Test
    fun `advanced mode preserves the pre-resolution profile and IPv6 config`() {
        val profile = profile().copy(
            transport = XrayTransport.Raw(),
            muxEnabled = true,
            fragmentEnabled = true,
            security = (profile().security as XraySecurity.Reality).copy(
                fingerprint = XrayFingerprintSetting.Fixed(XrayFingerprint.CHROME),
            ),
        )
        val config = XrayClientConfigFactory.createRuntimeConfig(
            profile,
            TunnelSettings(advancedMode = true, ipv6Enabled = true),
        )
        val preResolutionConfig = XrayClientConfigFactory.build(profile, ipv6Enabled = true).toJson()
        val root = XrayJson.default.parseToJsonElement(config.rawJson).jsonObject
        val rules = root.getValue("routing").jsonObject.getValue("rules").jsonArray.map { it.jsonObject }
        val stream = root.outbound("proxy").getValue("streamSettings").jsonObject
        val reality = stream.getValue("realitySettings").jsonObject

        assertEquals(normalizeVolatileValues(preResolutionConfig), normalizeVolatileValues(config.rawJson))
        assertTrue(config.ipv6Enabled)
        assertFalse(rules.any { rule -> rule["ip"]?.jsonArray?.any { it.jsonPrimitive.content == "::/0" } == true })
        assertTrue(root.outbound("proxy").getValue("mux").jsonObject.getValue("enabled").jsonPrimitive.content.toBoolean())
        assertTrue(stream.containsKey("finalmask"))
        assertEquals("chrome", reality.getValue("fingerprint").jsonPrimitive.content)
    }

    @Test
    fun `normal mode keeps the last working fingerprint first for Auto`() {
        val autoProfile = profile().copy(
            security = (profile().security as XraySecurity.Reality).copy(
                fingerprint = XrayFingerprintSetting.Auto(XrayFingerprint.SAFARI),
            ),
        )

        val config = XrayClientConfigFactory.createRuntimeConfig(
            autoProfile,
            TunnelSettings(advancedMode = false, ipv6Enabled = false),
        )

        assertEquals(XrayFingerprint.SAFARI, config.fingerprintCandidates.first().fingerprint)
        assertEquals(XrayFingerprint.SAFARI, config.fingerprint)
    }

    @Test
    fun `excluded apps are sorted into runtime config in both modes`() {
        val packages = setOf("org.example.bank_app", "com.example.bank.app")

        listOf(false, true).forEach { advancedMode ->
            val config = XrayClientConfigFactory.createRuntimeConfig(
                profile(),
                TunnelSettings(
                    advancedMode = advancedMode,
                    ipv6Enabled = false,
                    excludedPackages = packages,
                ),
            )

            assertEquals(listOf("com.example.bank.app", "org.example.bank_app"), config.excludedPackages)
            assertFalse(config.toString().contains("org.example.bank_app"))
            assertTrue(config.toString().contains("excludedPackages=2"))
        }
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
        assertEquals(emptyList<String>(), config.excludedPackages)
        assertEquals("server-1", config.serverId)
        assertNull(config.settingsKey)
        assertEquals(SocksEndpoint("127.0.0.1", 10_808, "kaihi", "secret"), config.socksEndpoint)
        val reencoded = XrayJson.default.encodeToString(config)
        assertFalse(reencoded.contains("ipv6Enabled"))
        assertFalse(reencoded.contains("settingsKey"))
        assertFalse(reencoded.contains("excludedPackages"))
    }

    private fun JsonObject.outbound(tag: String) = getValue("outbounds").jsonArray
        .map { it.jsonObject }
        .single { it.getValue("tag").jsonPrimitive.content == tag }

    private fun normalizeVolatileValues(rawJson: String): JsonObject {
        val root = XrayJson.default.parseToJsonElement(rawJson).jsonObject
        val inbounds = root.getValue("inbounds").jsonArray.map { inbound ->
            val inboundObject = inbound.jsonObject
            val settings = inboundObject.getValue("settings").jsonObject
            val accounts = settings.getValue("accounts").jsonArray.map { account ->
                JsonObject(account.jsonObject + ("pass" to JsonPrimitive("<credential>")))
            }
            JsonObject(inboundObject + ("settings" to JsonObject(settings + ("accounts" to JsonArray(accounts)))))
        }
        val observatory = root.getValue("burstObservatory").jsonObject
        val pingConfig = JsonObject(
            observatory.getValue("pingConfig").jsonObject + ("destination" to JsonPrimitive("<probe>")),
        )

        return JsonObject(
            root +
                ("inbounds" to JsonArray(inbounds)) +
                ("burstObservatory" to JsonObject(observatory + ("pingConfig" to pingConfig))),
        )
    }

    private fun settings(ipv6Enabled: Boolean) = TunnelSettings(
        advancedMode = true,
        ipv6Enabled = ipv6Enabled,
    )
}
