package app.kaihi.android.feature.dashboard.impl.ui.mvi

import app.kaihi.android.core.vpn.XraySubscription
import app.kaihi.android.core.vpn.XraySubscriptionSource
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
