package app.kaihi.android.core.vpn

import app.kaihi.android.core.vpn.configfactory.XrayClientConfigFactory
import app.kaihi.android.core.vpn.spec.XrayFingerprint
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
            XrayClientConfigFactory.settingsKey(profile, ipv6Enabled = false),
            XrayClientConfigFactory.settingsKey(profile, ipv6Enabled = false),
        )
    }

    @Test
    fun `key changes when a generated setting changes`() {
        val profile = profile().copy(muxEnabled = true)
        val key = XrayClientConfigFactory.settingsKey(profile, ipv6Enabled = false)

        assertNotEquals(key, XrayClientConfigFactory.settingsKey(profile.copy(muxEnabled = false), false))
        assertNotEquals(key, XrayClientConfigFactory.settingsKey(profile.copy(muxConcurrency = 16), false))
        assertNotEquals(key, XrayClientConfigFactory.settingsKey(profile.copy(fragmentEnabled = true), false))
        assertNotEquals(
            key,
            XrayClientConfigFactory.settingsKey(
                profile.copy(security = (profile.security as XraySecurity.Reality).copy(fingerprint = XrayFingerprint.CHROME)),
                false,
            ),
        )
        assertNotEquals(key, XrayClientConfigFactory.settingsKey(profile, ipv6Enabled = true))
    }

    @Test
    fun `key ignores profile metadata that does not shape the generated config`() {
        val profile = profile()

        assertEquals(
            XrayClientConfigFactory.settingsKey(profile, false),
            XrayClientConfigFactory.settingsKey(profile.copy(name = "renamed"), false),
        )
        assertEquals(
            XrayClientConfigFactory.settingsKey(profile, false),
            XrayClientConfigFactory.settingsKey(profile.copy(countryCode = "RU"), false),
        )
    }

    @Test
    fun `runtime config carries the profile settings key`() {
        val profile = profile()
        val runtimeConfig = XrayClientConfigFactory.createRuntimeConfig(profile, ipv6Enabled = true)

        assertEquals(
            XrayClientConfigFactory.settingsKey(profile, ipv6Enabled = true),
            runtimeConfig.settingsKey,
        )
    }

    @Test
    fun `key is lowercase SHA-256 hex and does not contain the profile UUID`() {
        val profile = profile()
        val key = XrayClientConfigFactory.settingsKey(profile, ipv6Enabled = false)

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

    private companion object {
        val LOWERCASE_HEX = Regex("[0-9a-f]{64}")
    }
}
