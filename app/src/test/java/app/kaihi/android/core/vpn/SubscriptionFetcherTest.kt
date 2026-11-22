package app.kaihi.android.core.vpn

import app.kaihi.android.core.storage.InMemoryAppStorage
import app.kaihi.android.core.mode.AppMode
import app.kaihi.android.core.mode.AppModeRepository
import java.nio.charset.StandardCharsets
import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionFetcherTest {

    @Test
    fun `http URL is rejected before transport`() = runTest {
        val transport = FakeHttpTransport()

        val result = fetcher(transport).fetch("http://panel.example/SECRETTOKEN", NOW)

        assertFailure(SubscriptionFetchFailure.INSECURE_URL, result)
        assertTrue(transport.requestedUrls.isEmpty())
    }

    @Test
    fun `http redirect is rejected`() = runTest {
        val transport = FakeHttpTransport(response(302, headers = mapOf("Location" to "http://other.example")))

        val result = fetcher(transport).fetch("https://panel.example/sub/SECRETTOKEN", NOW)

        assertFailure(SubscriptionFetchFailure.INSECURE_URL, result)
    }

    @Test
    fun `more than three redirects is rejected`() = runTest {
        val transport = FakeHttpTransport(
            response(302, headers = mapOf("Location" to "/one")),
            response(302, headers = mapOf("Location" to "/two")),
            response(302, headers = mapOf("Location" to "/three")),
            response(302, headers = mapOf("Location" to "/four")),
        )

        val result = fetcher(transport).fetch("https://panel.example/sub/SECRETTOKEN", NOW)

        assertFailure(SubscriptionFetchFailure.TOO_MANY_REDIRECTS, result)
        assertEquals(8, transport.requestedUrls.size)
    }

    @Test
    fun `relative redirect resolves against the current URL`() = runTest {
        val transport = FakeHttpTransport(
            response(302, headers = mapOf("Location" to "../next")),
            response(200, body = "vless://uuid@node.example:443"),
        )

        val result = fetcher(transport).fetch("https://panel.example/sub/path/SECRETTOKEN", NOW)

        assertEquals(
            listOf(
                "https://panel.example/sub/path/SECRETTOKEN",
                "https://panel.example/sub/next",
                "https://panel.example/sub/path/SECRETTOKEN",
            ),
            transport.requestedUrls,
        )
        assertTrue(result is SubscriptionFetchResult.Success)
    }

    @Test
    fun `non-200 status is rejected`() = runTest {
        val result = fetcher(FakeHttpTransport(response(404))).fetch("https://panel.example/sub/SECRETTOKEN", NOW)

        assertFailure(SubscriptionFetchFailure.HTTP_STATUS, result)
    }

    @Test
    fun `body over one MiB is rejected`() = runTest {
        val result = fetcher(
            FakeHttpTransport(response(200, body = ByteArray(1024 * 1024 + 1) { 'x'.code.toByte() })),
        ).fetch("https://panel.example/sub/SECRETTOKEN", NOW)

        assertFailure(SubscriptionFetchFailure.TOO_LARGE, result)
    }

    @Test
    fun `JSON and YAML formats are rejected`() = runTest {
        assertFailure(
            SubscriptionFetchFailure.UNSUPPORTED_FORMAT,
            fetcher(FakeHttpTransport(response(200, body = "{\"outbounds\":[]}"))).fetch(URL, NOW),
        )
        assertFailure(
            SubscriptionFetchFailure.UNSUPPORTED_FORMAT,
            fetcher(FakeHttpTransport(response(200, headers = mapOf("Content-Type" to listOf("application/json")), body = "vless://uuid@node.example:443"))).fetch(URL, NOW),
        )
        assertFailure(
            SubscriptionFetchFailure.UNSUPPORTED_FORMAT,
            fetcher(FakeHttpTransport(response(200, body = "proxies:\n  - name: node"))).fetch(URL, NOW),
        )
        assertFailure(
            SubscriptionFetchFailure.UNSUPPORTED_FORMAT,
            fetcher(FakeHttpTransport(response(200, body = "mixed-port: 7890"))).fetch(URL, NOW),
        )
    }

    @Test
    fun `base64 body links and metadata merge with response headers winning`() = runTest {
        val plain = "#profile-title: Body\n#announce: Body notice\nvless://uuid@node.example:443"
        val encoded = Base64.getEncoder().encodeToString(plain.toByteArray(StandardCharsets.UTF_8))
        val transport = FakeHttpTransport(
            response(
                200,
                headers = mapOf(
                    "profile-title" to listOf("Header"),
                    "announce" to listOf("Header notice"),
                    "profile-update-interval" to listOf("24"),
                ),
                body = encoded,
            ),
        )

        val result = fetcher(transport).fetch(URL, NOW)

        assertEquals(
            SubscriptionFetchResult.Success(
                content = SubscriptionContent.ShareLinks(listOf("vless://uuid@node.example:443")),
                metadata = SubscriptionMetadata(
                    title = "Header",
                    updateIntervalHours = 24,
                    announce = "Header notice",
                ),
                truncated = false,
            ),
            result,
        )
    }

    @Test
    fun `default mode accepts Xray JSON from Happ and uses its metadata`() = runTest {
        val transport = UserAgentTransport(
            mapOf(
                HAPP_USER_AGENT to response(
                    200,
                    headers = mapOf("profile-title" to listOf("JSON panel")),
                    body = XRAY_JSON,
                ),
            ),
        )

        val result = fetcher(transport).fetch(URL, NOW)

        val success = result as SubscriptionFetchResult.Success
        val content = success.content as SubscriptionContent.XrayJson
        assertEquals(1, content.subscription.profiles.size)
        assertEquals(SubscriptionMetadata(title = "JSON panel"), success.metadata)
        assertEquals(listOf(HAPP_USER_AGENT), transport.userAgents)
    }

    @Test
    fun `default mode falls back to Kaihi for a base64-only panel`() = runTest {
        val encoded = encodedLinks()
        val transport = UserAgentTransport(
            mapOf(
                HAPP_USER_AGENT to response(200, body = encoded),
                KAIHI_USER_AGENT to response(200, body = encoded),
            ),
        )

        val result = fetcher(transport).fetch(URL, NOW)

        assertEquals(
            SubscriptionContent.ShareLinks(listOf("vless://uuid@node.example:443")),
            (result as SubscriptionFetchResult.Success).content,
        )
        assertEquals(listOf(HAPP_USER_AGENT, KAIHI_USER_AGENT), transport.userAgents)
    }

    @Test
    fun `empty or non-200 Happ responses fall back to Kaihi`() = runTest {
        val encoded = encodedLinks()
        listOf(
            response(404),
            response(200, body = "[]"),
        ).forEach { happResponse ->
            val transport = UserAgentTransport(
                mapOf(
                    HAPP_USER_AGENT to happResponse,
                    KAIHI_USER_AGENT to response(200, body = encoded),
                ),
            )

            val result = fetcher(transport).fetch(URL, NOW)

            assertTrue(result is SubscriptionFetchResult.Success)
            assertEquals(listOf(HAPP_USER_AGENT, KAIHI_USER_AGENT), transport.userAgents)
        }
    }

    @Test
    fun `Happ-specific non-JSON response is never returned`() = runTest {
        val transport = UserAgentTransport(
            mapOf(
                HAPP_USER_AGENT to response(200, body = "happ://crypt-secret"),
                KAIHI_USER_AGENT to response(200, body = encodedLinks()),
            ),
        )

        val result = fetcher(transport).fetch(URL, NOW)

        assertEquals(
            SubscriptionContent.ShareLinks(listOf("vless://uuid@node.example:443")),
            (result as SubscriptionFetchResult.Success).content,
        )
    }

    @Test
    fun `custom advanced User-Agent gets exactly one request and accepts JSON`() = runTest {
        val storage = InMemoryAppStorage()
        val preferences = VpnPreferences(storage).apply { setSubscriptionUserAgent("MyClient/1") }
        val mode = AppModeRepository(storage).apply { setMode(AppMode.ADVANCED) }
        val transport = UserAgentTransport(mapOf("MyClient/1" to response(200, body = XRAY_JSON)))
        val subscriptionFetcher = SubscriptionFetcher(transport, preferences, mode)

        val result = subscriptionFetcher.fetch(URL, NOW)

        assertTrue((result as SubscriptionFetchResult.Success).content is SubscriptionContent.XrayJson)
        assertEquals(listOf("MyClient/1"), transport.userAgents)
    }

    @Test
    fun `custom advanced User-Agent accepts base64 links`() = runTest {
        val storage = InMemoryAppStorage()
        val preferences = VpnPreferences(storage).apply { setSubscriptionUserAgent("MyClient/1") }
        val mode = AppModeRepository(storage).apply { setMode(AppMode.ADVANCED) }
        val transport = UserAgentTransport(mapOf("MyClient/1" to response(200, body = encodedLinks())))
        val subscriptionFetcher = SubscriptionFetcher(transport, preferences, mode)

        val result = subscriptionFetcher.fetch(URL, NOW)

        assertTrue((result as SubscriptionFetchResult.Success).content is SubscriptionContent.ShareLinks)
        assertEquals(listOf("MyClient/1"), transport.userAgents)
    }

    @Test
    fun `JSON content type does not reject a JSON array and JSON object stays unsupported`() = runTest {
        val jsonTransport = UserAgentTransport(
            mapOf(
                HAPP_USER_AGENT to response(200, body = "[]"),
                KAIHI_USER_AGENT to response(
                    200,
                    headers = mapOf("Content-Type" to listOf("application/json")),
                    body = "{\"outbounds\":[]}",
                ),
            ),
        )

        assertFailure(
            SubscriptionFetchFailure.UNSUPPORTED_FORMAT,
            fetcher(jsonTransport).fetch(URL, NOW),
        )
        assertEquals(listOf(HAPP_USER_AGENT, KAIHI_USER_AGENT), jsonTransport.userAgents)
    }

    @Test
    fun `JSON success toString contains no profile address, id, or URL`() = runTest {
        val result = fetcher(
            UserAgentTransport(mapOf(HAPP_USER_AGENT to response(200, body = XRAY_JSON))),
        ).fetch(URL, NOW)

        val diagnostics = result.toString()
        assertFalse(diagnostics.contains("node.example"))
        assertFalse(diagnostics.contains(UUID))
        assertFalse(diagnostics.contains(URL))
    }

    @Test
    fun `body with only metadata is empty`() = runTest {
        val result = fetcher(
            FakeHttpTransport(response(200, body = "#profile-title: Only metadata")),
        ).fetch(URL, NOW)

        assertFailure(SubscriptionFetchFailure.EMPTY, result)
    }

    @Test
    fun `malformed UTF-8 is unsupported`() = runTest {
        val result = fetcher(FakeHttpTransport(response(200, body = byteArrayOf(0xC3.toByte(), 0x28)))).fetch(URL, NOW)

        assertFailure(SubscriptionFetchFailure.UNSUPPORTED_FORMAT, result)
    }

    @Test
    fun `custom User-Agent is sent only in advanced mode`() = runTest {
        val storage = InMemoryAppStorage()
        val preferences = VpnPreferences(storage).apply { setSubscriptionUserAgent("PanelClient/1.2") }
        val mode = AppModeRepository(storage)
        val transport = FakeHttpTransport(response(200, body = "vless://uuid@node.example:443"))
        val fetcher = SubscriptionFetcher(transport, preferences, mode)

        fetcher.fetch(URL, NOW)
        assertEquals("Happ/3.0", transport.requestHeaders[0]["User-Agent"])
        assertEquals("Kaihi/${app.kaihi.android.BuildConfig.VERSION_NAME}", transport.requestHeaders[1]["User-Agent"])

        mode.setMode(AppMode.ADVANCED)
        fetcher.fetch(URL, NOW)
        assertEquals("PanelClient/1.2", transport.requestHeaders.last()["User-Agent"])
    }

    @Test
    fun `result toString never reveals the subscription credential`() = runTest {
        val result = fetcher(
            FakeHttpTransport(response(200, body = "vless://uuid@node.example:443/SECRETTOKEN?token=SECRETTOKEN")),
        ).fetch(URL, NOW)

        assertFalse(result.toString().contains("SECRETTOKEN"))
    }

    private fun fetcher(transport: HttpTransport): SubscriptionFetcher {
        val storage = InMemoryAppStorage()
        return SubscriptionFetcher(
            httpTransport = transport,
            vpnPreferences = VpnPreferences(storage),
            appModeRepository = AppModeRepository(storage),
        )
    }

    private fun assertFailure(expected: SubscriptionFetchFailure, result: SubscriptionFetchResult) {
        assertEquals(SubscriptionFetchResult.Failure(expected), result)
    }

    private fun encodedLinks(): String = Base64.getEncoder().encodeToString(
        "vless://uuid@node.example:443".toByteArray(StandardCharsets.UTF_8),
    )

    private class UserAgentTransport(
        private val responses: Map<String, HttpTransportResponse>,
    ) : HttpTransport {
        val userAgents = mutableListOf<String>()

        override suspend fun get(
            url: String,
            requestHeaders: Map<String, String>,
            bodyLimit: Int,
        ): HttpTransportResponse {
            val userAgent = requireNotNull(requestHeaders["User-Agent"])
            userAgents += userAgent
            return requireNotNull(responses[userAgent]) { "No response for $userAgent" }
        }
    }

    private companion object {
        const val URL = "https://panel.example/sub/SECRETTOKEN"
        const val NOW = 123L
        const val HAPP_USER_AGENT = "Happ/3.0"
        const val KAIHI_USER_AGENT = "Kaihi/${app.kaihi.android.BuildConfig.VERSION_NAME}"
        const val UUID = "0f1e2d3c-4b5a-4987-8a6b-5c4d3e2f1a0b"
        const val PUBLIC_KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"

        val XRAY_JSON = """
            [{
              "remarks":"JSON node",
              "outbounds":[{
                "protocol":"vless",
                "settings":{"vnext":[{"address":"node.example","port":443,"users":[{"id":"$UUID","encryption":"none","flow":"xtls-rprx-vision"}]}]},
                "streamSettings":{"network":"tcp","tcpSettings":{},"security":"reality","realitySettings":{"serverName":"cover.example","publicKey":"$PUBLIC_KEY","shortId":"0011","fingerprint":"firefox"}}
              }]
            }]
        """.trimIndent()

        fun response(
            statusCode: Int,
            headers: Map<String, List<String>> = emptyMap(),
            body: String = "",
        ): HttpTransportResponse = FakeHttpTransport.response(statusCode, headers, body)

        fun response(
            statusCode: Int,
            headers: Map<String, List<String>> = emptyMap(),
            body: ByteArray,
        ): HttpTransportResponse = FakeHttpTransport.response(statusCode, headers, body)

        fun response(
            statusCode: Int,
            headers: Map<String, String>,
        ): HttpTransportResponse = FakeHttpTransport.response(statusCode, headers)
    }
}
