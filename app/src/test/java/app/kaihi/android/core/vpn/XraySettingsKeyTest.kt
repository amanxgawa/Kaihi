package app.kaihi.android.core.vpn

import app.kaihi.android.core.vpn.configfactory.XrayClientConfigFactory
import app.kaihi.android.core.vpn.spec.XrayFingerprint
import app.kaihi.android.core.vpn.spec.XrayFingerprintSetting
import app.kaihi.android.core.vpn.spec.XraySecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XraySettingsKeyTest {
    @Test
    fun `same profile and IPv6 setting produce the same key`() {
        val profile = profile()

        assertEquals(
            XrayClientConfigFactory.settingsKey(profile, settings(ipv6Enabled = false)),
            XrayClientConfigFactory.settingsKey(profile, settings(ipv6Enabled = false)),
        )
    }

    @Test
    fun `key changes when a generated setting changes`() {
        val profile = profile().copy(muxEnabled = true)
        val key = XrayClientConfigFactory.settingsKey(profile, settings(ipv6Enabled = false))

        assertNotEquals(key, XrayClientConfigFactory.settingsKey(profile.copy(muxEnabled = false), settings(false)))
        assertNotEquals(key, XrayClientConfigFactory.settingsKey(profile.copy(muxConcurrency = 16), settings(false)))
        assertNotEquals(key, XrayClientConfigFactory.settingsKey(profile.copy(fragmentEnabled = true), settings(false)))
        assertNotEquals(
            key,
            XrayClientConfigFactory.settingsKey(
                profile.copy(security = (profile.security as XraySecurity.Reality).copy(
                    fingerprint = XrayFingerprintSetting.Fixed(XrayFingerprint.CHROME),
                )),
                settings(false),
            ),
        )
        assertNotEquals(key, XrayClientConfigFactory.settingsKey(profile, settings(ipv6Enabled = true)))
    }

    @Test
    fun `auto key ignores last working while fixed keys track the fingerprint`() {
        val profile = profile()
        val reality = profile.security as XraySecurity.Reality

        assertEquals(
            XrayClientConfigFactory.settingsKey(profile, settings(false)),
            XrayClientConfigFactory.settingsKey(
                profile.copy(security = reality.copy(fingerprint = XrayFingerprintSetting.Auto(XrayFingerprint.CHROME))),
                settings(false),
            ),
        )
        assertNotEquals(
            XrayClientConfigFactory.settingsKey(profile, settings(false)),
            XrayClientConfigFactory.settingsKey(
                profile.copy(security = reality.copy(fingerprint = XrayFingerprintSetting.Fixed(XrayFingerprint.FIREFOX))),
                settings(false),
            ),
        )
        assertNotEquals(
            XrayClientConfigFactory.settingsKey(
                profile.copy(security = reality.copy(fingerprint = XrayFingerprintSetting.Fixed(XrayFingerprint.FIREFOX))),
                settings(false),
            ),
            XrayClientConfigFactory.settingsKey(
                profile.copy(security = reality.copy(fingerprint = XrayFingerprintSetting.Fixed(XrayFingerprint.SAFARI))),
                settings(false),
            ),
        )
    }

    @Test
    fun `key ignores profile metadata that does not shape the generated config`() {
        val profile = profile()

        assertEquals(
            XrayClientConfigFactory.settingsKey(profile, settings(false)),
            XrayClientConfigFactory.settingsKey(profile.copy(name = "renamed"), settings(false)),
        )
        assertEquals(
            XrayClientConfigFactory.settingsKey(profile, settings(false)),
            XrayClientConfigFactory.settingsKey(profile.copy(countryCode = "RU"), settings(false)),
        )
    }

    @Test
    fun `runtime config carries the profile settings key`() {
        val profile = profile()
        val runtimeConfig = XrayClientConfigFactory.createRuntimeConfig(profile, settings(ipv6Enabled = true))

        assertEquals(
            XrayClientConfigFactory.settingsKey(profile, settings(ipv6Enabled = true)),
            runtimeConfig.settingsKey,
        )
    }

    @Test
    fun `default profile key is unchanged when switching modes`() {
        val profile = profile()

        assertEquals(
            XrayClientConfigFactory.settingsKey(profile, settings(ipv6Enabled = false, advancedMode = false)),
            XrayClientConfigFactory.settingsKey(profile, settings(ipv6Enabled = false, advancedMode = true)),
        )
    }

    @Test
    fun `advanced values produce different keys when advanced mode is enabled`() {
        val profile = profile().copy(
            muxEnabled = true,
            fragmentEnabled = true,
            security = (profile().security as XraySecurity.Reality).copy(
                fingerprint = XrayFingerprintSetting.Fixed(XrayFingerprint.CHROME),
            ),
        )

        assertNotEquals(
            XrayClientConfigFactory.settingsKey(profile, settings(ipv6Enabled = true, advancedMode = false)),
            XrayClientConfigFactory.settingsKey(profile, settings(ipv6Enabled = true, advancedMode = true)),
        )
    }

    @Test
    fun `key is lowercase SHA-256 hex and does not contain the profile UUID`() {
        val profile = profile()
        val key = XrayClientConfigFactory.settingsKey(profile, settings(ipv6Enabled = false))

        assertEquals(64, key.length)
        assertTrueLowercaseHex(key)
        assertFalse(key.contains(profile.uuid))
    }

    private fun assertTrueLowercaseHex(value: String) {
        assertTrue("expected lowercase hexadecimal, got: $value", value.matches(LOWERCASE_HEX))
    }

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

    private fun settings(ipv6Enabled: Boolean, advancedMode: Boolean = true) = TunnelSettings(
        advancedMode = advancedMode,
        ipv6Enabled = ipv6Enabled,
    )

    private companion object {
        val LOWERCASE_HEX = Regex("[0-9a-f]{64}")
    }
}
