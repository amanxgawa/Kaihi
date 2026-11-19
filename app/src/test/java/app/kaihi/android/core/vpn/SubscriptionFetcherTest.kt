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

        val result = fetcher(transport).fetch("http://panel.example/SECRETTOKEN")

        assertFailure(SubscriptionFetchFailure.INSECURE_URL, result)
        assertTrue(transport.requestedUrls.isEmpty())
    }

    @Test
    fun `http redirect is rejected`() = runTest {
        val transport = FakeHttpTransport(response(302, headers = mapOf("Location" to "http://other.example")))

        val result = fetcher(transport).fetch("https://panel.example/sub/SECRETTOKEN")

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

        val result = fetcher(transport).fetch("https://panel.example/sub/SECRETTOKEN")

        assertFailure(SubscriptionFetchFailure.TOO_MANY_REDIRECTS, result)
        assertEquals(4, transport.requestedUrls.size)
    }

    @Test
    fun `relative redirect resolves against the current URL`() = runTest {
        val transport = FakeHttpTransport(
            response(302, headers = mapOf("Location" to "../next")),
            response(200, body = "vless://uuid@node.example:443"),
        )

        val result = fetcher(transport).fetch("https://panel.example/sub/path/SECRETTOKEN")

        assertEquals(listOf("https://panel.example/sub/path/SECRETTOKEN", "https://panel.example/sub/next"), transport.requestedUrls)
        assertTrue(result is SubscriptionFetchResult.Success)
    }

    @Test
    fun `non-200 status is rejected`() = runTest {
        val result = fetcher(FakeHttpTransport(response(404))).fetch("https://panel.example/sub/SECRETTOKEN")

        assertFailure(SubscriptionFetchFailure.HTTP_STATUS, result)
    }

    @Test
    fun `body over one MiB is rejected`() = runTest {
        val result = fetcher(
            FakeHttpTransport(response(200, body = ByteArray(1024 * 1024 + 1) { 'x'.code.toByte() })),
        ).fetch("https://panel.example/sub/SECRETTOKEN")

        assertFailure(SubscriptionFetchFailure.TOO_LARGE, result)
    }

    @Test
    fun `JSON and YAML formats are rejected`() = runTest {
        assertFailure(
            SubscriptionFetchFailure.UNSUPPORTED_FORMAT,
            fetcher(FakeHttpTransport(response(200, body = "{\"outbounds\":[]}"))).fetch(URL),
        )
        assertFailure(
            SubscriptionFetchFailure.UNSUPPORTED_FORMAT,
            fetcher(FakeHttpTransport(response(200, headers = mapOf("Content-Type" to listOf("application/json")), body = "vless://uuid@node.example:443"))).fetch(URL),
        )
        assertFailure(
            SubscriptionFetchFailure.UNSUPPORTED_FORMAT,
            fetcher(FakeHttpTransport(response(200, body = "proxies:\n  - name: node"))).fetch(URL),
        )
        assertFailure(
            SubscriptionFetchFailure.UNSUPPORTED_FORMAT,
            fetcher(FakeHttpTransport(response(200, body = "mixed-port: 7890"))).fetch(URL),
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

        val result = fetcher(transport).fetch(URL)

        assertEquals(
            SubscriptionFetchResult.Success(
                links = listOf("vless://uuid@node.example:443"),
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
    fun `body with only metadata is empty`() = runTest {
        val result = fetcher(
            FakeHttpTransport(response(200, body = "#profile-title: Only metadata")),
        ).fetch(URL)

        assertFailure(SubscriptionFetchFailure.EMPTY, result)
    }

    @Test
    fun `malformed UTF-8 is unsupported`() = runTest {
        val result = fetcher(FakeHttpTransport(response(200, body = byteArrayOf(0xC3.toByte(), 0x28)))).fetch(URL)

        assertFailure(SubscriptionFetchFailure.UNSUPPORTED_FORMAT, result)
    }

    @Test
    fun `custom User-Agent is sent only in advanced mode`() = runTest {
        val storage = InMemoryAppStorage()
        val preferences = VpnPreferences(storage).apply { setSubscriptionUserAgent("PanelClient/1.2") }
        val mode = AppModeRepository(storage)
        val transport = FakeHttpTransport(response(200, body = "vless://uuid@node.example:443"))
        val fetcher = SubscriptionFetcher(transport, preferences, mode)

        fetcher.fetch(URL)
        assertEquals(
            mapOf("User-Agent" to "Kaihi/${app.kaihi.android.BuildConfig.VERSION_NAME}", "Accept" to "*/*"),
            transport.requestHeaders.single(),
        )

        mode.setMode(AppMode.ADVANCED)
        fetcher.fetch(URL)
        assertEquals(
            mapOf("User-Agent" to "PanelClient/1.2", "Accept" to "*/*"),
            transport.requestHeaders.last(),
        )
    }

    @Test
    fun `result toString never reveals the subscription credential`() = runTest {
        val result = fetcher(
            FakeHttpTransport(response(200, body = "vless://uuid@node.example:443/SECRETTOKEN?token=SECRETTOKEN")),
        ).fetch(URL)

        assertFalse(result.toString().contains("SECRETTOKEN"))
    }

    private fun fetcher(transport: FakeHttpTransport): SubscriptionFetcher {
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

    private companion object {
        const val URL = "https://panel.example/sub/SECRETTOKEN"

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
