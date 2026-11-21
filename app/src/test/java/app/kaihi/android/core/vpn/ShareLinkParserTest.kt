package app.kaihi.android.core.vpn

import app.kaihi.android.core.vpn.spec.XhttpMode
import app.kaihi.android.core.vpn.spec.XrayFingerprint
import app.kaihi.android.core.vpn.spec.XrayFingerprintSetting
import app.kaihi.android.core.vpn.spec.XrayFlow
import app.kaihi.android.core.vpn.spec.XrayProtocol
import app.kaihi.android.core.vpn.spec.XraySecurity
import app.kaihi.android.core.vpn.spec.XrayTransport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShareLinkParserTest {

    @Test
    fun `parses Xray discussion REALITY link conventions and case-insensitive fingerprint`() {
        // Source: XTLS/Xray-core discussion #716, sections 4.2-4.4; REALITY fields follow its sni/pbk/sid/fp convention.
        val result = parseShareLink(realityLink("&fp=ChRoMe") + "#Edge%20Node", 123L)
        val profile = success(result)

        assertEquals("a1b2c3d4-e5f6-7890-abcd-ef1234567890", profile.credential)
        assertEquals("Edge Node", profile.name)
        assertEquals("example.com", profile.serverAddress)
        assertEquals(443, profile.serverPort)
        assertEquals(
            XraySecurity.Reality(
                serverName = "www.example.com",
                publicKey = PUBLIC_KEY,
                shortId = "a1b2",
                fingerprint = XrayFingerprintSetting.Auto(XrayFingerprint.CHROME),
            ),
            profile.security,
        )
        assertEquals(XrayTransport.Raw(), profile.transport)
        assertNull(profile.ssh)
        assertEquals(123L, profile.addedAtMillis)
        assertNull(profile.countryCode)
        assertFalse(profile.muxEnabled)
        assertFalse(profile.fragmentEnabled)
    }

    @Test
    fun `parses 3x-ui style REALITY raw vision share link`() {
        // Source: 3x-ui one-click share-link output shape; project documents VLESS, REALITY, Vision and Raw support.
        val link = "vless://a1b2c3d4-e5f6-7890-abcd-ef1234567890@203.0.113.8:443" +
            "?type=raw&security=reality&encryption=none&sni=cdn.example.com&pbk=$PUBLIC_KEY&sid=0011" +
            "&flow=xtls-rprx-vision#Panel%20node"
        val profile = success(parseShareLink(link, 7L))

        assertEquals("203.0.113.8", profile.serverAddress)
        assertEquals("Panel node", profile.name)
        assertEquals(XrayTransport.Raw(XrayFlow.VISION), profile.transport)
    }

    @Test
    fun `parses Remnawave style REALITY xhttp share link`() {
        // Source: Remnawave panel's Xray subscription/share-link format; XHTTP uses type, path and mode fields from Xray #716.
        val link = "vless://a1b2c3d4-e5f6-7890-abcd-ef1234567890@node.example:443" +
            "?security=reality&sni=cover.example&pbk=$PUBLIC_KEY&sid=&fp=firefox" +
            "&type=xhttp&path=%2Fedge%2Fconnect&mode=packet-up&host=cover.example#EU%20%F0%9F%8C%90"
        val profile = success(parseShareLink(link, 9L))

        assertEquals("EU 🌐", profile.name)
        assertEquals(XrayTransport.Xhttp("/edge/connect", XhttpMode.PACKET_UP), profile.transport)
    }

    @Test
    fun `parses TLS WebSocket with a decoded path query and host`() {
        val link = "vless://$UUID@cdn.example.com:443?security=tls&sni=cdn.example.com" +
            "&type=ws&path=%2Fws%3Fed%3D2048&host=cdn.example.com#WS"
        val profile = success(parseShareLink(link, 0L))

        assertEquals(XrayTransport.WebSocket("/ws?ed=2048", "cdn.example.com"), profile.transport)
        assertEquals(XraySecurity.Tls("cdn.example.com"), profile.security)
    }

    @Test
    fun `WebSocket treats an empty host as absent and preserves its path`() {
        val link = "vless://$UUID@cdn.example.com:443?security=tls&sni=cdn.example.com" +
            "&type=ws&path=%2Fws&host=#x"
        val profile = success(parseShareLink(link, 0L))

        assertEquals(XrayTransport.WebSocket("/ws", null), profile.transport)
    }

    @Test
    fun `WebSocket defaults an empty path and host to absent values`() {
        val link = "vless://$UUID@cdn.example.com:443?security=tls&sni=cdn.example.com" +
            "&type=ws&path=&host=#x"
        val profile = success(parseShareLink(link, 0L))

        assertEquals(XrayTransport.WebSocket("/", null), profile.transport)
    }

    @Test
    fun `gRPC defaults an empty service name and authority to absent values`() {
        val link = "vless://$UUID@g.example.com:443?security=tls&sni=g.example.com" +
            "&type=grpc&serviceName=&authority=&mode=gun#x"
        val profile = success(parseShareLink(link, 0L))

        assertEquals(XrayTransport.Grpc("", false, null), profile.transport)
    }

    @Test
    fun `Trojan HTTPUpgrade defaults an empty path and host to absent values`() {
        val profile = success(
            parseShareLink("trojan://pw@h.example.com:443?type=httpupgrade&path=&host=#x", 0L),
        )

        assertEquals(XrayTransport.HttpUpgrade("/", null), profile.transport)
        assertEquals(XraySecurity.Tls("h.example.com"), profile.security)
    }

    @Test
    fun `parses Trojan TLS HTTPUpgrade without a host override`() {
        val profile = success(
            parseShareLink("trojan://pw@cdn.example.com:443?security=tls&type=httpupgrade&path=%2Fup#HU", 0L),
        )

        assertEquals(XrayProtocol.TROJAN, profile.protocol)
        assertEquals(XrayTransport.HttpUpgrade("/up", null), profile.transport)
        assertEquals(XraySecurity.Tls("cdn.example.com"), profile.security)
    }

    @Test
    fun `parses REALITY gRPC with multi mode service name and authority`() {
        val link = "vless://$UUID@grpc.example.com:443?security=reality&sni=grpc.example.com" +
            "&pbk=$PUBLIC_KEY&sid=ab12&type=grpc&serviceName=gun-svc&mode=multi&authority=grpc.example.com#G"
        val profile = success(parseShareLink(link, 0L))

        assertEquals(XrayTransport.Grpc("gun-svc", multiMode = true, authority = "grpc.example.com"), profile.transport)
        assertEquals(
            XraySecurity.Reality(serverName = "grpc.example.com", publicKey = PUBLIC_KEY, shortId = "ab12"),
            profile.security,
        )
    }

    @Test
    fun `gRPC defaults to an empty service name and gun mode with TLS or REALITY`() {
        listOf("vless://$UUID@example.com:443?security=tls", realityLink()).forEach { base ->
            listOf("", "&mode=gun", "&mode=").forEach { mode ->
                val profile = success(parseShareLink("$base&type=grpc$mode", 0L))

                assertEquals(XrayTransport.Grpc("", false, null), profile.transport)
            }
        }
    }

    @Test
    fun `WebSocket and HTTPUpgrade default paths for VLESS and Trojan`() {
        listOf("vless://$UUID", "trojan://pw").forEach { prefix ->
            listOf("ws" to XrayTransport.WebSocket(), "httpupgrade" to XrayTransport.HttpUpgrade()).forEach { (type, expected) ->
                val profile = success(parseShareLink("$prefix@example.com:443?security=tls&type=$type&flow=", 0L))

                assertEquals(expected, profile.transport)
            }
        }
    }

    @Test
    fun `rejects REALITY with WebSocket and HTTPUpgrade`() {
        listOf("ws", "httpupgrade").forEach { type ->
            assertUnsupported(parseShareLink(realityLink("&type=$type"), 0L), UnsupportedReason.TRANSPORT_OPTION)
        }
    }

    @Test
    fun `rejects relative WebSocket and HTTPUpgrade paths`() {
        listOf("ws", "httpupgrade").forEach { type ->
            assertUnsupported(
                parseShareLink("vless://$UUID@example.com:443?security=tls&type=$type&path=ws", 0L),
                UnsupportedReason.TRANSPORT_OPTION,
            )
        }
    }

    @Test
    fun `rejects unknown gRPC modes`() {
        assertUnsupported(parseShareLink(realityLink("&type=grpc&mode=guna"), 0L), UnsupportedReason.TRANSPORT_OPTION)
    }

    @Test
    fun `rejects invalid gRPC service names`() {
        listOf("a%20b", "a".repeat(257), "a%3Fb", "a%0Ab").forEach { serviceName ->
            assertUnsupported(
                parseShareLink(realityLink("&type=grpc&serviceName=$serviceName"), 0L),
                UnsupportedReason.TRANSPORT_OPTION,
            )
        }
    }

    @Test
    fun `accepts the gRPC service name character set and maximum length`() {
        listOf("pkg.Service/method_1.2-3", "a".repeat(256)).forEach { serviceName ->
            val profile = success(parseShareLink(realityLink("&type=grpc&serviceName=$serviceName"), 0L))

            assertEquals(XrayTransport.Grpc(serviceName), profile.transport)
        }
    }

    @Test
    fun `rejects invalid transport hosts and gRPC authorities as invalid hosts`() {
        listOf("ws" to "host", "httpupgrade" to "host", "grpc" to "authority").forEach { (type, parameter) ->
            assertEquals(
                ShareLinkResult.Invalid(InvalidReason.INVALID_HOST),
                parseShareLink("vless://$UUID@example.com:443?security=tls&type=$type&$parameter=bad_host!", 0L),
            )
        }
    }

    @Test
    fun `rejects nonempty flow for WebSocket HTTPUpgrade and gRPC`() {
        listOf("vless://$UUID", "trojan://pw").forEach { prefix ->
            listOf("ws", "httpupgrade", "grpc").forEach { type ->
                assertUnsupported(
                    parseShareLink("$prefix@example.com:443?security=tls&type=$type&flow=xtls-rprx-vision", 0L),
                    UnsupportedReason.TRANSPORT_OPTION,
                )
            }
        }
    }

    @Test
    fun `accepts custom ids and bracketed IPv6 literals without resolving hostnames`() {
        val custom = success(parseShareLink(realityLink(id = "custom-id_7"), 1L))
        val ipv6 = success(
            parseShareLink(
                "vless://$UUID@[2001:db8::7]:8443?security=tls&sni=cover.example&type=tcp#v6",
                2L,
            ),
        )

        assertEquals("custom-id_7", custom.credential)
        assertEquals("2001:db8::7", ipv6.serverAddress)
        assertEquals(XraySecurity.Tls("cover.example"), ipv6.security)
    }

    @Test
    fun `accepts RFC 4291 and RFC 5952 IPv6 text forms and preserves their spelling`() {
        // Source: RFC 4291 sections 2.2 and 2.5.3; RFC 5952 section 4 examples of compression and expanded forms.
        val addresses = listOf(
            "::1",
            "2001:db8::1",
            "2001:0db8:0000:0000:0000:0000:0000:0001",
            "::ffff:192.0.2.1",
            "fe80::",
        )

        addresses.forEach { address ->
            val profile = success(
                parseShareLink("vless://$UUID@[$address]:443?security=tls&sni=cover.example", 1L),
            )
            assertEquals(address, profile.serverAddress)
        }
    }

    @Test
    fun `rejects malformed IPv6 text forms`() {
        listOf(
            "2001:db8:::1",
            "1::2::3",
            "12345::",
            "2001:db8::g",
            "fe80::1%wlan0",
            "::ffff:999.0.2.1",
            "a:b:c:d:e:f:a:b:c",
        ).forEach { address ->
            assertInvalid(
                parseShareLink("vless://$UUID@[$address]:443?security=tls&sni=cover.example", 1L),
            )
        }
    }

    @Test
    fun `accepts a case-insensitive VLESS scheme and dotted IPv4 server address`() {
        val link = realityLink().replaceFirst("vless://", "VLESS://")
            .replace("example.com:443", "192.0.2.4:443")

        assertEquals("192.0.2.4", success(parseShareLink(link, 0L)).serverAddress)
    }

    @Test
    fun `normalizes uuid case and applies name cleanup by unicode code point`() {
        val mixedCase = "A1B2C3D4-E5F6-7890-ABCD-EF1234567890"
        val profile = success(
            parseShareLink(realityLink(id = mixedCase) + "#%00%20A%F0%9F%99%82%7F%20", 0L),
        )

        assertEquals(UUID, profile.credential)
        assertEquals("A🙂", profile.name)

        val longName = "x".repeat(63) + "🙂z"
        val capped = success(parseShareLink(realityLink() + "#" + longName, 0L)).name
        assertEquals(64, capped.codePointCount(0, capped.length))
        assertTrue(capped.endsWith("🙂"))
    }

    @Test
    fun `defaults empty names to a deterministic nickname`() {
        val profile = success(parseShareLink(realityLink() + "#%00%0A", 0L))

        assertEquals(generateNickname("example.com"), profile.name)
    }

    @Test
    fun `uses TLS host as SNI fallback only when it is a hostname`() {
        val fallback = success(parseShareLink("vless://$UUID@example.com:443?security=tls", 0L))

        assertEquals(XraySecurity.Tls("example.com"), fallback.security)
        assertInvalid(parseShareLink("vless://$UUID@203.0.113.8:443?security=tls", 0L))
    }

    @Test
    fun `parses xhttp defaults and decoded path`() {
        val profile = success(
            parseShareLink(realityLink("&type=xhttp&path=%2Fone+two") + "#x", 0L),
        )

        assertEquals(XrayTransport.Xhttp("/one+two", XhttpMode.AUTO), profile.transport)
    }

    @Test
    fun `treats decoded empty values as absent for all parameters`() {
        val emptyShortIdLink = realityLink().replace("&sid=a1b2", "&sid=")
        val xhttp = success(
            parseShareLink(
                emptyShortIdLink + "&type=xhttp&encryption=&fp=&host=&path=&mode=&extra=&spx=",
                1L,
            ),
        )
        val raw = success(
            parseShareLink(
                emptyShortIdLink + "&type=tcp&encryption=&fp=&headerType=&flow=",
                2L,
            ),
        )
        val tls = success(parseShareLink("vless://$UUID@example.com:443?security=tls&sni=", 3L))

        assertEquals(XrayTransport.Xhttp("/", XhttpMode.AUTO), xhttp.transport)
        assertEquals(
            XraySecurity.Reality(
                serverName = "www.example.com",
                publicKey = PUBLIC_KEY,
                shortId = "",
                fingerprint = XrayFingerprintSetting.Auto(),
            ),
            xhttp.security,
        )
        assertEquals(XrayTransport.Raw(), raw.transport)
        assertEquals(XraySecurity.Tls("example.com"), tls.security)
    }

    @Test
    fun `requires a nonempty SNI for REALITY and still rejects duplicate empty parameters`() {
        val missingRealitySni = parseShareLink(
            "vless://$UUID@example.com:443?security=reality&sni=&pbk=$PUBLIC_KEY",
            0L,
        )
        val duplicate = parseShareLink("vless://$UUID@example.com:443?security=&security=tls", 0L)

        assertTrue(missingRealitySni is ShareLinkResult.Invalid)
        assertEquals(InvalidReason.INVALID_HOST, (missingRealitySni as ShareLinkResult.Invalid).reason)
        assertTrue(duplicate is ShareLinkResult.Invalid)
        assertEquals(InvalidReason.DUPLICATE_PARAMETER, (duplicate as ShareLinkResult.Invalid).reason)
    }

    @Test
    fun `parses 3x-ui style REALITY TCP vision export with empty parameters`() {
        val link = "vless://$UUID@203.0.113.8:443" +
            "?type=tcp&encryption=&security=reality&sni=cdn.example.com&pbk=$PUBLIC_KEY" +
            "&sid=&fp=&headerType=&spx=&flow=xtls-rprx-vision#Panel%20TCP"
        val parsed = success(parseShareLink(link, 17L))
        val expected = XrayVpnProfile(
            id = parsed.id,
            name = "Panel TCP",
            serverAddress = "203.0.113.8",
            serverPort = 443,
            credential = UUID,
            security = XraySecurity.Reality(
                serverName = "cdn.example.com",
                publicKey = PUBLIC_KEY,
                shortId = "",
                fingerprint = XrayFingerprintSetting.Auto(),
            ),
            transport = XrayTransport.Raw(XrayFlow.VISION),
            addedAtMillis = 17L,
        )

        assertEquals(expected, parsed)
    }

    @Test
    fun `parses 3x-ui style REALITY xhttp export with empty parameters`() {
        val link = "vless://$UUID@node.example:8443" +
            "?type=xhttp&encryption=&security=reality&sni=mask.example&pbk=$PUBLIC_KEY" +
            "&sid=&fp=&host=&path=%2F&mode=&flow=&spx=#Panel%20XHTTP"
        val parsed = success(parseShareLink(link, 23L))
        val expected = XrayVpnProfile(
            id = parsed.id,
            name = "Panel XHTTP",
            serverAddress = "node.example",
            serverPort = 8443,
            credential = UUID,
            security = XraySecurity.Reality(
                serverName = "mask.example",
                publicKey = PUBLIC_KEY,
                shortId = "",
                fingerprint = XrayFingerprintSetting.Auto(),
            ),
            transport = XrayTransport.Xhttp("/", XhttpMode.AUTO),
            addedAtMillis = 23L,
        )

        assertEquals(expected, parsed)
    }

    @Test
    fun `uses automatic fingerprint when the value is missing unknown or randomized`() {
        listOf("", "&fp=not-a-fingerprint", "&fp=random", "&fp=randomized").forEach { fp ->
            val profile = success(parseShareLink(realityLink(fp), 0L))
            assertEquals(XrayFingerprintSetting.Auto(), (profile.security as XraySecurity.Reality).fingerprint)
        }
    }

    @Test
    fun `round trips supported exporter profiles`() {
        val reality = XraySecurity.Reality(
            serverName = "cover.example",
            publicKey = PUBLIC_KEY,
            shortId = "0011",
            fingerprint = XrayFingerprintSetting.Auto(XrayFingerprint.FIREFOX),
        )
        val profiles = listOf(
            profile("Reality xhttp", reality, XrayTransport.Xhttp("/edge", XhttpMode.STREAM_ONE)),
            profile("Reality vision", reality, XrayTransport.Raw(XrayFlow.VISION)),
            profile("TLS xhttp", XraySecurity.Tls("tls.example"), XrayTransport.Xhttp("/tls", XhttpMode.AUTO)),
        )

        profiles.forEach { original ->
            val parsed = success(parseShareLink(original.toShareUri(), original.addedAtMillis + 1L))
            assertEquals(
                original.copy(id = parsed.id, addedAtMillis = parsed.addedAtMillis),
                parsed,
            )
        }
    }

    @Test
    fun `parses Trojan REALITY TCP links with decoded password and Unicode name`() {
        val link = "trojan://s3cr3t%40pw@nl.example.com:443" +
            "?security=reality&sni=nl.example.com&pbk=$PUBLIC_KEY&sid=ab12&fp=firefox&type=tcp" +
            "#%F0%9F%87%B3%F0%9F%87%B1%20NL-TN-01"
        val profile = success(parseShareLink(link, 1L))

        assertEquals(XrayProtocol.TROJAN, profile.protocol)
        assertEquals("s3cr3t@pw", profile.credential)
        assertEquals("nl.example.com", profile.serverAddress)
        assertEquals(443, profile.serverPort)
        assertEquals(
            XraySecurity.Reality(
                serverName = "nl.example.com",
                publicKey = PUBLIC_KEY,
                shortId = "ab12",
                fingerprint = XrayFingerprintSetting.Auto(XrayFingerprint.FIREFOX),
            ),
            profile.security,
        )
        assertEquals(XrayTransport.Raw(), profile.transport)
        assertEquals("🇳🇱 NL-TN-01", profile.name)

        val ignoredEncryption = success(
            parseShareLink("trojan://pw@example.com:443?security=tls&encryption=ignored", 2L),
        )
        assertEquals(XrayProtocol.TROJAN, ignoredEncryption.protocol)
    }

    @Test
    fun `Trojan without security defaults to TLS using the hostname as SNI`() {
        val profile = success(parseShareLink("trojan://pw@example.com:443#x", 0L))

        assertEquals(XrayProtocol.TROJAN, profile.protocol)
        assertEquals(XraySecurity.Tls("example.com"), profile.security)
    }

    @Test
    fun `Trojan rejects no security and nonempty flow as unsupported`() {
        assertUnsupported(
            parseShareLink("trojan://pw@example.com:443?security=none", 0L),
            UnsupportedReason.NO_SECURITY,
        )
        assertUnsupported(
            parseShareLink("trojan://pw@example.com:443?security=tls&flow=xtls-rprx-vision", 0L),
            UnsupportedReason.TRANSPORT_OPTION,
        )
    }

    @Test
    fun `rejects invalid Trojan passwords without revealing them`() {
        val cases = listOf(
            "trojan://pw%20x@example.com:443" to "pw x",
            "trojan://@example.com:443" to "",
            "trojan://pw%00x@example.com:443" to "pw\u0000x",
            "trojan://${"x".repeat(129)}@example.com:443" to "x".repeat(129),
        )

        cases.forEach { (link, password) ->
            val result = parseShareLink(link, 0L)
            assertTrue("expected invalid result, got $result", result is ShareLinkResult.Invalid)
            assertEquals(InvalidReason.INVALID_PASSWORD, (result as ShareLinkResult.Invalid).reason)
            if (password.isNotEmpty()) assertFalse(result.toString().contains(password))
        }
    }

    @Test
    fun `accepts a case-insensitive Trojan scheme and keeps vmess unsupported`() {
        assertEquals(
            XrayProtocol.TROJAN,
            success(parseShareLink("TROJAN://pw@example.com:443", 0L)).protocol,
        )
        assertUnsupported(parseShareLink("vmess://$UUID@example.com:443", 0L), UnsupportedReason.PROTOCOL)
        assertInvalid(parseShareLink("ftp://example.com/path", 0L))
        assertInvalid(parseShareLink("not a uri", 0L))
    }

    @Test
    fun `returns unsupported protocol for known schemes other than VLESS and Trojan`() {
        listOf("vmess", "ss", "hysteria2", "tuic").forEach { scheme ->
            assertUnsupported(
                parseShareLink("$scheme://$UUID@example.com:443", 0L),
                UnsupportedReason.PROTOCOL,
            )
        }
    }

    @Test
    fun `returns unsupported encryption and security results`() {
        assertUnsupported(parseShareLink(realityLink("&encryption=mlkem768x25519"), 0L), UnsupportedReason.ENCRYPTION)
        assertUnsupported(parseShareLink("vless://$UUID@example.com:443?security=none", 0L), UnsupportedReason.NO_SECURITY)
        assertUnsupported(parseShareLink("vless://$UUID@example.com:443", 0L), UnsupportedReason.NO_SECURITY)
        assertUnsupported(parseShareLink("vless://$UUID@example.com:443?security=xtls", 0L), UnsupportedReason.SECURITY)
        // Source: XTLS/Xray-core discussion #716, section 5 VLESS + TCP + XTLS example.
        assertUnsupported(
            parseShareLink(
                "vless://b0dd64e4-0fbd-4038-9139-d1f32a68a0dc@qv2ray.net:3279?security=xtls&flow=rprx-xtls-splice#VLESSTCPXTLSSplice",
                0L,
            ),
            UnsupportedReason.SECURITY,
        )
    }

    @Test
    fun `returns unsupported when TLS disables certificate verification`() {
        listOf("1", "true", "TrUe").forEach { value ->
            assertUnsupported(
                parseShareLink("vless://$UUID@example.com:443?security=tls&sni=example.com&allowInsecure=$value", 0L),
                UnsupportedReason.INSECURE_TLS,
            )
        }
    }

    @Test
    fun `returns unsupported transport and transport option values`() {
        listOf("kcp", "mkcp", "http", "h2", "quic").forEach { type ->
            assertUnsupported(parseShareLink(realityLink("&type=$type"), 0L), UnsupportedReason.TRANSPORT)
        }
        listOf(
            "&type=tcp&headerType=http",
            "&type=tcp&flow=xtls-rprx-direct",
            "&type=xhttp&mode=unknown",
            "&type=xhttp&path=relative",
            "&type=xhttp&host=other.example",
            "&type=xhttp&extra=1",
        ).forEach { options ->
            assertUnsupported(parseShareLink(realityLink(options), 0L), UnsupportedReason.TRANSPORT_OPTION)
        }
    }

    @Test
    fun `rejects malformed links with enum-only reasons`() {
        val cases = listOf(
            "vless://$UUID@example.com:443?security=reality&pbk=$PUBLIC_KEY&sni=example.com" + "x".repeat(8192) to InvalidReason.TOO_LONG,
            "vless://bad%20id@example.com:443?security=reality&pbk=$PUBLIC_KEY&sni=example.com" to InvalidReason.INVALID_ID,
            "vless://${"é".repeat(16)}@example.com:443?security=reality&pbk=$PUBLIC_KEY&sni=example.com" to InvalidReason.INVALID_ID,
            "vless://$UUID@bad_host.example:443?security=reality&pbk=$PUBLIC_KEY&sni=example.com" to InvalidReason.INVALID_HOST,
            "vless://$UUID@999.999.999.999:443?security=reality&pbk=$PUBLIC_KEY&sni=example.com" to InvalidReason.INVALID_HOST,
            "vless://$UUID@[2001:db8:::1]:443?security=reality&pbk=$PUBLIC_KEY&sni=example.com" to InvalidReason.MALFORMED_URI,
            "vless://$UUID@2001:db8::1:443?security=reality&pbk=$PUBLIC_KEY&sni=example.com" to InvalidReason.INVALID_HOST,
            "vless://$UUID@example.com?security=reality&pbk=$PUBLIC_KEY&sni=example.com" to InvalidReason.INVALID_PORT,
            "vless://$UUID@example.com:0?security=reality&pbk=$PUBLIC_KEY&sni=example.com" to InvalidReason.INVALID_PORT,
            "vless://$UUID@example.com:65536?security=reality&pbk=$PUBLIC_KEY&sni=example.com" to InvalidReason.INVALID_PORT,
            "vless://$UUID@example.com:nope?security=reality&pbk=$PUBLIC_KEY&sni=example.com" to InvalidReason.INVALID_PORT,
            "vless://$UUID@example.com:443?security=reality&security=tls" to InvalidReason.DUPLICATE_PARAMETER,
            "vless://$UUID@example.com:443?security=reality%ZZ" to InvalidReason.MALFORMED_URI,
            "vless://%FF@example.com:443?security=reality" to InvalidReason.INVALID_ENCODING,
            "vless://$UUID@example.com:443?security=reality&pbk=short&sni=example.com" to InvalidReason.INVALID_PUBLIC_KEY,
            "vless://$UUID@example.com:443?security=reality&sni=example.com" to InvalidReason.INVALID_PUBLIC_KEY,
            "vless://$UUID@example.com:443?security=reality&pbk=$PUBLIC_KEY&sni=bad_host" to InvalidReason.INVALID_HOST,
            "vless://$UUID@example.com:443?security=reality&pbk=$PUBLIC_KEY&sni=example.com&sid=abc" to InvalidReason.INVALID_SHORT_ID,
            "vless://$UUID@example.com:443?security=reality&pbk=$PUBLIC_KEY&sni=example.com&sid=0123456789abcdef00" to InvalidReason.INVALID_SHORT_ID,
        )

        cases.forEachIndexed { index, (link, reason) ->
            val result = parseShareLink(link, 0L)
            assertTrue("expected $reason but got $result", result is ShareLinkResult.Invalid)
            assertEquals("case $index", reason, (result as ShareLinkResult.Invalid).reason)
        }
    }

    @Test
    fun `invalid and unsupported outcomes never reveal the supplied UUID`() {
        val fakeUuid = "f00dbabe-1111-2222-3333-deadbeefcafe"
        val outcomes = listOf(
            parseShareLink("vless://$fakeUuid@example.com:443?security=none", 0L),
            parseShareLink("vless://$fakeUuid@bad_host:443?security=reality", 0L),
            parseShareLink("vless://$fakeUuid@example.com:443?security=tls&allowInsecure=1", 0L),
        )

        outcomes.forEach { result ->
            assertFalse(result.toString(), result.toString().contains(fakeUuid))
            assertTrue(result is ShareLinkResult.Invalid || result is ShareLinkResult.Unsupported)
        }
    }

    private fun profile(name: String, security: XraySecurity, transport: XrayTransport) = XrayVpnProfile(
        id = "profile-id",
        name = name,
        serverAddress = "203.0.113.8",
        serverPort = 443,
        credential = UUID,
        security = security,
        transport = transport,
        addedAtMillis = 42L,
    )

    private fun realityLink(options: String = "", id: String = UUID): String =
        "vless://$id@example.com:443?security=reality&sni=www.example.com" +
            "&pbk=$PUBLIC_KEY&sid=a1b2$options"

    private fun success(result: ShareLinkResult): XrayVpnProfile {
        assertTrue("expected successful parse, got $result", result is ShareLinkResult.Success)
        return (result as ShareLinkResult.Success).profile
    }

    private fun assertInvalid(result: ShareLinkResult) {
        assertTrue("expected invalid result, got $result", result is ShareLinkResult.Invalid)
    }

    private fun assertUnsupported(result: ShareLinkResult, reason: UnsupportedReason) {
        assertTrue("expected unsupported result, got $result", result is ShareLinkResult.Unsupported)
        assertEquals(reason, (result as ShareLinkResult.Unsupported).reason)
    }

    private companion object {
        const val UUID = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
        const val PUBLIC_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
    }
}
