package app.kaihi.android.feature.dashboard.impl.ui.mvi

import app.kaihi.android.core.vpn.XraySubscription
import app.kaihi.android.core.vpn.XraySubscriptionSource
import app.kaihi.android.core.vpn.XraySubscriptionUserInfo
import app.kaihi.android.core.vpn.XrayVpnProfile
import app.kaihi.android.core.vpn.spec.XraySecurity
import app.kaihi.android.feature.dashboard.impl.domain.model.ServerLibrary
import app.kaihi.android.feature.dashboard.impl.ui.model.SubscriptionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DashboardStateMappersTest {
    @Test
    fun `Imported is first and empty Imported groups are hidden`() {
        val deployed = subscription("deployed", XraySubscriptionSource.Deployed)
        val remote = subscription("remote", XraySubscriptionSource.Remote("https://sub.example/config"))
        val imported = subscription(XraySubscription.IMPORTED_ID, XraySubscriptionSource.Imported)
        val emptyImported = XraySubscription(
            id = "empty-imported",
            name = "",
            servers = emptyList(),
            source = XraySubscriptionSource.Imported,
        )

        val mapped = ServerLibrary(
            subscriptions = listOf(deployed, remote, emptyImported, imported),
            selectedServer = null,
            collapsedSubscriptionIds = emptySet(),
        ).toSubscriptionsUi()

        assertEquals(listOf(XraySubscription.IMPORTED_ID, "deployed", "remote"), mapped.map { it.id })
        assertEquals(SubscriptionKind.IMPORTED, mapped.first().kind)
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
        ).toSubscriptionsUi().single()

        assertEquals(10L, editor.usedBytes)
        assertEquals(100L, editor.totalBytes)
        assertEquals(500L, editor.expireEpochSeconds)
        assertEquals(6, editor.updateIntervalHours)
        assertEquals(1234L, editor.lastUpdatedMillis)
        assertEquals(10L, mappedGroup.remote?.usedBytes)
        assertFalse(editor.toString().contains("private-token"))
        assertFalse(mappedGroup.toString().contains("private-token"))
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
