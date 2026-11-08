package app.kaihi.android.feature.dashboard

import app.kaihi.android.core.vpn.XrayEngineState
import app.kaihi.android.core.vpn.XrayVpnProfile
import app.kaihi.android.core.vpn.configfactory.XrayClientConfigFactory
import app.kaihi.android.core.vpn.spec.XraySecurity
import app.kaihi.android.feature.dashboard.impl.ui.mvi.reconnectToApplyServerId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReconnectToApplyTest {

    @Test
    fun `running session with unchanged saved profile has no pending changes`() {
        val profile = profile()

        assertNull(result(profile, profile, ipv6Enabled = false))
    }

    @Test
    fun `running session reports a changed fragment setting`() {
        val sessionProfile = profile()

        assertEquals(
            sessionProfile.id,
            result(sessionProfile, sessionProfile.copy(fragmentEnabled = true), ipv6Enabled = false),
        )
    }

    @Test
    fun `running session reports a changed IPv6 preference`() {
        val sessionProfile = profile()

        assertEquals(sessionProfile.id, result(sessionProfile, sessionProfile, ipv6Enabled = true))
    }

    @Test
    fun `reverting the saved profile clears pending changes`() {
        val sessionProfile = profile()
        val changedProfile = sessionProfile.copy(fragmentEnabled = true)

        assertEquals(sessionProfile.id, result(sessionProfile, changedProfile, ipv6Enabled = false))
        assertNull(result(sessionProfile, sessionProfile, ipv6Enabled = false))
    }

    @Test
    fun `non-running sessions never report pending changes`() {
        val sessionProfile = profile()
        val changedProfile = sessionProfile.copy(fragmentEnabled = true)

        listOf(XrayEngineState.STARTING, XrayEngineState.IDLE).forEach { state ->
            assertNull(
                reconnectToApplyServerId(
                    engineState = state,
                    sessionServerId = sessionProfile.id,
                    sessionSettingsKey = XrayClientConfigFactory.settingsKey(sessionProfile, false),
                    sessionServer = changedProfile,
                    ipv6Enabled = false,
                ),
            )
        }
    }

    @Test
    fun `missing session settings key never reports pending changes`() {
        val sessionProfile = profile()

        assertNull(
            reconnectToApplyServerId(
                engineState = XrayEngineState.RUNNING,
                sessionServerId = sessionProfile.id,
                sessionSettingsKey = null,
                sessionServer = sessionProfile.copy(fragmentEnabled = true),
                ipv6Enabled = false,
            ),
        )
    }

    @Test
    fun `missing saved server never reports pending changes`() {
        val sessionProfile = profile()

        assertNull(
            reconnectToApplyServerId(
                engineState = XrayEngineState.RUNNING,
                sessionServerId = sessionProfile.id,
                sessionSettingsKey = XrayClientConfigFactory.settingsKey(sessionProfile, false),
                sessionServer = null,
                ipv6Enabled = false,
            ),
        )
    }

    private fun result(
        sessionProfile: XrayVpnProfile,
        savedProfile: XrayVpnProfile?,
        ipv6Enabled: Boolean,
    ): String? = reconnectToApplyServerId(
        engineState = XrayEngineState.RUNNING,
        sessionServerId = sessionProfile.id,
        sessionSettingsKey = XrayClientConfigFactory.settingsKey(sessionProfile, false),
        sessionServer = savedProfile,
        ipv6Enabled = ipv6Enabled,
    )

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
}
