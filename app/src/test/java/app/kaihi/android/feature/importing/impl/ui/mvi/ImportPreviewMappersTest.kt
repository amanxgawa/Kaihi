package app.kaihi.android.feature.importing.impl.ui.mvi

import app.kaihi.android.core.vpn.SubscriptionMetadata
import app.kaihi.android.core.vpn.VlessLinkResult
import app.kaihi.android.core.vpn.XraySubscriptionUserInfo
import app.kaihi.android.core.vpn.XrayVpnProfile
import app.kaihi.android.core.vpn.parseVlessLink
import app.kaihi.android.feature.importing.impl.domain.ImportCounts
import app.kaihi.android.feature.importing.impl.domain.ImportPreparation
import app.kaihi.android.feature.importing.impl.domain.ImportPreview
import app.kaihi.android.feature.importing.impl.ui.model.PreviewLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ImportPreviewMappersTest {
    @Test
    fun `groups servers by country and sorts groups by size then code`() {
        val preview = ImportPreview.Links(
            profiles = listOf("🇳🇱 a", "🇳🇱 b", "🇩🇪 c", "plain").map { profile().copy(name = it) },
            counts = ImportCounts(),
        ).toUi(NOW)

        assertEquals(listOf("NL", "DE", null), preview.groups.map { it.countryCode })
        assertEquals(listOf(2, 1, 1), preview.groups.map { it.serverNames.size })
    }

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
        val displayed = listOf(preview.title, requireNotNull(preview.announce)) + preview.groups.flatMap { it.serverNames }
        listOf('\u202E', '\u2066', '\u2069').forEach { control ->
            assertFalse(displayed.any { control in it })
        }

        val existing = ImportPreparation.AlreadyAdded("subscription", "a\u202Eb", "server").toUi()
        assertFalse('\u202E' in existing.name)
    }

    @Test
    fun `maps expiry levels around now and the three day warning window`() {
        assertEquals(PreviewLevel.ERROR, subscription(expireSeconds = (NOW - 1_000) / 1_000).toUi(NOW).expiryLevel)
        assertEquals(PreviewLevel.ERROR, subscription(expireSeconds = NOW / 1_000).toUi(NOW).expiryLevel)
        assertEquals(
            PreviewLevel.WARNING,
            subscription(expireSeconds = (NOW + 2 * DAY_MILLIS) / 1_000).toUi(NOW).expiryLevel,
        )
        assertEquals(
            PreviewLevel.NORMAL,
            subscription(expireSeconds = (NOW + 10 * DAY_MILLIS) / 1_000).toUi(NOW).expiryLevel,
        )
        assertEquals(PreviewLevel.NORMAL, subscription().toUi(NOW).expiryLevel)
    }

    @Test
    fun `maps traffic levels and leaves unknown totals normal`() {
        assertEquals(PreviewLevel.WARNING, subscription(upload = 95, total = 100).toUi(NOW).trafficLevel)
        assertEquals(PreviewLevel.ERROR, subscription(upload = 100, total = 100).toUi(NOW).trafficLevel)
        assertEquals(PreviewLevel.NORMAL, subscription(upload = 50, total = 100).toUi(NOW).trafficLevel)
        val noTotal = subscription(upload = 50).toUi(NOW)
        assertEquals(PreviewLevel.NORMAL, noTotal.trafficLevel)
        assertNull(noTotal.totalBytes)
    }

    private fun subscription(
        expireSeconds: Long? = null,
        upload: Long? = null,
        total: Long? = null,
    ): ImportPreview.Subscription = ImportPreview.Subscription(
        url = "https://sub.example/config",
        host = "sub.example",
        metadata = SubscriptionMetadata(
            userInfo = if (expireSeconds == null && upload == null && total == null) {
                null
            } else {
                XraySubscriptionUserInfo(
                    uploadBytes = upload,
                    downloadBytes = 0,
                    totalBytes = total,
                    expireEpochSeconds = expireSeconds,
                )
            },
        ),
        profiles = listOf(profile()),
        counts = ImportCounts(),
        fetchedAtMillis = NOW,
    )

    private fun profile(): XrayVpnProfile =
        (parseVlessLink(VALID_LINK, NOW) as VlessLinkResult.Success).profile

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L
        const val VALID_LINK =
            "vless://a1b2c3d4-e5f6-7890-abcd-ef1234567890@203.0.113.8:443" +
                "?type=raw&security=reality&encryption=none&sni=cdn.example.com&pbk=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA&sid=0011" +
                "&flow=xtls-rprx-vision#Panel%20node"
    }
}
