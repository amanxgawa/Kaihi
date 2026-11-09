package app.kaihi.android.core.vpn

import java.nio.charset.StandardCharsets
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionMetadataParserTest {

    @Test
    fun `parses plain links and recognized body metadata while ignoring comments`() {
        // Source: INCY subscription-format docs, mixed format example with #announce metadata and plain VLESS links.
        val body = """
            
              vless://uuid@one.example:443?security=tls#One
            #PROFILE-TITLE:  My panel  
            #profile-update-interval: 24
            #ignored-key: not metadata
            #announce: Maintenance tonight
            #support-url: https://support.example/help
            #profile-web-page-url: https://provider.example/
            trojan://secret@two.example:443#Two
        """.trimIndent()

        val parsed = parseSubscriptionBody(body)

        assertEquals(
            listOf("vless://uuid@one.example:443?security=tls#One", "trojan://secret@two.example:443#Two"),
            parsed.links,
        )
        assertEquals("My panel", parsed.metadata.title)
        assertEquals(24, parsed.metadata.updateIntervalHours)
        assertEquals("Maintenance tonight", parsed.metadata.announce)
        assertEquals("https://support.example/help", parsed.metadata.supportUrl)
        assertEquals("https://provider.example/", parsed.metadata.webPageUrl)
        assertFalse(parsed.truncated)
    }

    @Test
    fun `decodes padded unpadded URL-safe and whitespace-wrapped base64 bodies`() {
        // Source: INCY subscription-format docs, base64-encoded links (standard and URL-safe forms are documented).
        val plain = "vless://uuid@node.example:443?security=tls#🌐"
        val standard = Base64.getEncoder().encodeToString(plain.toByteArray(StandardCharsets.UTF_8))
        val urlSafe = Base64.getUrlEncoder().withoutPadding().encodeToString(plain.toByteArray(StandardCharsets.UTF_8))
        val lineWrapped = standard.chunked(12).joinToString("\r\n")

        assertTrue(urlSafe.contains('-') || urlSafe.contains('_'))
        assertEquals(listOf(plain), parseSubscriptionBody(standard).links)
        assertEquals(listOf(plain), parseSubscriptionBody(standard.trimEnd('=' )).links)
        assertEquals(listOf(plain), parseSubscriptionBody(urlSafe).links)
        assertEquals(listOf(plain), parseSubscriptionBody(" \n$lineWrapped\t").links)
    }

    @Test
    fun `does not decode plain text that only resembles base64`() {
        val plainText = "This is a plain subscription note, not a link list"

        assertEquals(listOf(plainText), parseSubscriptionBody(plainText).links)
    }

    @Test
    fun `parses documented base64 profile title and keeps only its description first line`() {
        // Source: INCY subscription-format docs, Profile Title example with a base64 name followed by description.
        val headers = mapOf(
            "PrOfIlE-TiTlE" to listOf("base64:TWVNdiBWUE4KV2VsY29tZSB0byBvdXIgc2VydmljZQ=="),
        )
        val body = parseSubscriptionBody(
            "#profile-title: base64:TWVNdiBWUE4KV2VsY29tZSB0byBvdXIgc2VydmljZQ==\nvless://uuid@node.example:443",
        )

        assertEquals("MeMv VPN", parseSubscriptionHeaders(headers).title)
        assertEquals("MeMv VPN", body.metadata.title)
    }

    @Test
    fun `parses user info aliases drops bad pairs and treats zero expiry as no expiry`() {
        // Source: INCY subscription-format docs, Subscription User Info header example and field definitions.
        val metadata = parseSubscriptionHeaders(
            mapOf(
                "SUBSCRIPTION-USERINFO" to listOf(
                    " upload = 12 ; download= 34 ; totl = 100 ; expire=0 ; broken ; total=-1 ; upload=oops ",
                ),
            ),
        )

        assertEquals(
            XraySubscriptionUserInfo(uploadBytes = 12L, downloadBytes = 34L, totalBytes = 100L),
            metadata.userInfo,
        )
        assertNull(parseSubscriptionHeaders(mapOf("subscription-userinfo" to listOf("expire=0;bad=x"))).userInfo)
    }

    @Test
    fun `validates interval range and HTTPS-only metadata links`() {
        val parsed = parseSubscriptionHeaders(
            mapOf(
                "profile-update-interval" to listOf("8760"),
                "support-url" to listOf("https://support.example/path"),
                "profile-web-page-url" to listOf("https://provider.example"),
            ),
        )
        assertEquals(8760, parsed.updateIntervalHours)
        assertEquals("https://support.example/path", parsed.supportUrl)
        assertEquals("https://provider.example", parsed.webPageUrl)

        listOf("0", "8761", "12.5", "-1").forEach { interval ->
            assertNull(parseSubscriptionHeaders(mapOf("profile-update-interval" to listOf(interval))).updateIntervalHours)
        }
        listOf("javascript:alert(1)", "http://support.example", "https:/missing-host", "https://").forEach { url ->
            assertNull(parseSubscriptionHeaders(mapOf("support-url" to listOf(url))).supportUrl)
        }
    }

    @Test
    fun `headers override body metadata field by field`() {
        val body = parseSubscriptionBody(
            "#profile-title: Body\n#announce: body notice\n#profile-update-interval: 12\nvless://uuid@node.example:443",
        ).metadata
        val headers = parseSubscriptionHeaders(
            mapOf("profile-title" to listOf("Header"), "announce" to listOf("header notice")),
        )

        assertEquals(
            SubscriptionMetadata(title = "Header", updateIntervalHours = 12, announce = "header notice"),
            body.overriddenBy(headers),
        )
    }

    @Test
    fun `caps body link lines at five hundred and reports truncation`() {
        val body = (1..501).joinToString("\n") { "vless://uuid@node$it.example:443?security=tls" }

        val parsed = parseSubscriptionBody(body)

        assertEquals(500, parsed.links.size)
        assertTrue(parsed.truncated)
    }

    @Test
    fun `caps announce and input metadata values`() {
        val announce = "x".repeat(1100)
        val parsed = parseSubscriptionHeaders(mapOf("announce" to listOf(announce)))

        val parsedAnnounce = requireNotNull(parsed.announce)
        assertEquals(1024, parsedAnnounce.codePointCount(0, parsedAnnounce.length))
        assertEquals("x".repeat(64), parseSubscriptionHeaders(mapOf("profile-title" to listOf("x".repeat(80)))).title)
    }
}
