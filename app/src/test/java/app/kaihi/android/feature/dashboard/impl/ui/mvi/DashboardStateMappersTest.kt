package app.kaihi.android.feature.dashboard.impl.ui.mvi

import app.kaihi.android.core.vpn.XraySubscription
import app.kaihi.android.core.vpn.XraySubscriptionSource
import app.kaihi.android.core.vpn.XraySubscriptionUserInfo
import app.kaihi.android.core.vpn.XrayVpnProfile
import app.kaihi.android.core.vpn.spec.XraySecurity
import app.kaihi.android.feature.dashboard.impl.domain.model.ServerLibrary
import app.kaihi.android.platform.ui.components.UsageLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DashboardStateMappersTest {
    @Test
    fun `flag in imported server name maps to server row and details`() {
        val profile = XrayVpnProfile(
            id = "server-imported",
            name = "\uD83C\uDDF3\uD83C\uDDF1 Amsterdam",
            serverAddress = "server.example",
            serverPort = 443,
            uuid = "123e4567-e89b-12d3-a456-426614174000",
            security = XraySecurity.Tls("cover.example"),
            addedAtMillis = 0L,
        )
        val subscription = XraySubscription(
            id = "imported",
            name = "Imported",
            servers = listOf(profile),
            source = XraySubscriptionSource.Imported,
        )
        val serverUi = ServerLibrary(
            subscriptions = listOf(subscription),
            selectedServer = null,
            collapsedSubscriptionIds = emptySet(),
        ).toSubscriptionsUi(nowMillis = 0L).single().also { assertEquals(null, it.usage) }.servers.single()
        val detailsUi = profile.toDetailsUi()

        assertEquals("Amsterdam", serverUi.name)
        assertEquals("\uD83C\uDDF3\uD83C\uDDF1", serverUi.flag)
        assertEquals("Amsterdam", detailsUi.name)
        assertEquals("\uD83C\uDDF3\uD83C\uDDF1", detailsUi.flag)
    }

    @Test
    fun `remote subscription metadata maps to UI without exposing its credential URL`() {
        val remote = subscription(
            "remote",
            XraySubscriptionSource.Remote(
                url = "https://provider.example/private-token",
                userInfo = XraySubscriptionUserInfo(
                    uploadBytes = 3L,
                    downloadBytes = 7L,
                    totalBytes = 100L,
                    expireEpochSeconds = 500L,
                ),
                updateIntervalHours = 6,
                supportUrl = "https://provider.example/support",
                webPageUrl = "https://provider.example/account",
                announce = "Maintenance tonight",
                lastUpdatedMillis = 1234L,
            ),
        )
        val editor = remote.toEditorUi()
        val mappedGroup = ServerLibrary(
            subscriptions = listOf(remote),
            selectedServer = null,
            collapsedSubscriptionIds = emptySet(),
        ).toSubscriptionsUi(nowMillis = 0L).single()

        assertEquals(10L, editor.usedBytes)
        assertEquals(100L, editor.totalBytes)
        assertEquals(500L, editor.expireEpochSeconds)
        assertEquals(6, editor.updateIntervalHours)
        assertEquals(1234L, editor.lastUpdatedMillis)
        assertEquals(10L, mappedGroup.usage?.usedBytes)
        assertEquals(100L, mappedGroup.usage?.totalBytes)
        assertEquals("Maintenance tonight", mappedGroup.usage?.announce)
        assertEquals(UsageLevel.NORMAL, mappedGroup.usage?.trafficLevel)
        assertTrue(editor.isRemote)
        assertFalse(editor.toString().contains("private-token"))
        assertFalse(mappedGroup.toString().contains("private-token"))
    }

    @Test
    fun `deployed subscription editor is not remote`() {
        val deployed = subscription("deployed", XraySubscriptionSource.Deployed)

        assertFalse(deployed.toEditorUi().isRemote)
    }

    @Test
    fun `share effect does not reveal the shared text in its string representation`() {
        val effect = DashboardContract.Effect.ShareText("https://provider.example/private-token")

        assertFalse(effect.toString().contains("private-token"))
    }

    private fun subscription(id: String, source: XraySubscriptionSource) = XraySubscription(
        id = id,
        name = id,
        servers = listOf(
            XrayVpnProfile(
                id = "server-$id",
                name = "Server",
                serverAddress = "server.example",
                serverPort = 443,
                uuid = "123e4567-e89b-12d3-a456-426614174000",
                security = XraySecurity.Tls("cover.example"),
                addedAtMillis = 0L,
            ),
        ),
        source = source,
    )
}
