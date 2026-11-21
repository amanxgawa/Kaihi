package app.kaihi.android.feature.importing.impl.ui.mvi

import app.kaihi.android.core.vpn.SubscriptionMetadata
import app.kaihi.android.core.vpn.ShareLinkResult
import app.kaihi.android.core.vpn.XrayVpnProfile
import app.kaihi.android.core.vpn.parseShareLink
import app.kaihi.android.feature.importing.impl.domain.ImportCounts
import app.kaihi.android.feature.importing.impl.domain.ImportPreparation
import app.kaihi.android.feature.importing.impl.domain.ImportPreview
import org.junit.Assert.assertFalse
import org.junit.Test

class ImportPreviewMappersTest {
    @Test
    fun `cleans titles announcements server names and already added names`() {
        val preview = ImportPreview.Subscription(
            url = "https://sub.example/config",
            host = "sub.example",
            metadata = SubscriptionMetadata(title = "Bob\u202EVPN", announce = "\u2066hi\u2069"),
            profiles = listOf(profile().copy(name = "x\u202Ey")),
            counts = ImportCounts(),
            fetchedAtMillis = NOW,
        ).toUi(NOW)
        val displayed = listOf(preview.title, requireNotNull(preview.usage?.announce)) + preview.groups.flatMap { it.serverNames }
        listOf('\u202E', '\u2066', '\u2069').forEach { control ->
            assertFalse(displayed.any { control in it })
        }

        val existing = ImportPreparation.AlreadyAdded("a\u202Eb", "server").toUi()
        assertFalse('\u202E' in existing.name)
    }

    private fun profile(): XrayVpnProfile =
        (parseShareLink(VALID_LINK, NOW) as ShareLinkResult.Success).profile

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val VALID_LINK =
            "vless://a1b2c3d4-e5f6-7890-abcd-ef1234567890@203.0.113.8:443" +
                "?type=raw&security=reality&encryption=none&sni=cdn.example.com&pbk=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA&sid=0011" +
                "&flow=xtls-rprx-vision#Panel%20node"
    }
}
