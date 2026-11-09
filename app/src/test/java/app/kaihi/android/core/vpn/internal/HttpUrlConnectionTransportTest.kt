package app.kaihi.android.core.vpn.internal

import java.net.InetAddress
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HttpUrlConnectionTransportTest {

    @Test
    fun `returns status headers and body and sends the requested User-Agent`() = runTest {
        var request = ""
        val body = "vless://uuid@node.example:443".toByteArray(StandardCharsets.UTF_8)
        withLoopbackResponse(
            response = rawResponse(200, mapOf("X-Test" to "present"), body),
            onRequest = { request = it },
        ) { url ->
            val result = HttpUrlConnectionTransport(Dispatchers.Unconfined).get(
                url = url,
                requestHeaders = mapOf("User-Agent" to "PanelClient/1.2", "Accept" to "*/*"),
                bodyLimit = 1024,
            )

            assertEquals(200, result.statusCode)
            assertEquals(listOf("present"), result.headers["X-Test"])
            assertArrayEquals(body, result.body)
        }

        assertTrue(request.lines().contains("User-Agent: PanelClient/1.2"))
        assertTrue(request.lines().contains("Accept: */*"))
    }

    @Test
    fun `reads only limit plus one body bytes`() = runTest {
        val body = "0123456789".toByteArray(StandardCharsets.US_ASCII)

        withLoopbackResponse(rawResponse(200, emptyMap(), body)) { url ->
            val result = HttpUrlConnectionTransport(Dispatchers.Unconfined).get(
                url = url,
                requestHeaders = emptyMap(),
                bodyLimit = 4,
            )

            assertArrayEquals(body.copyOf(5), result.body)
        }
    }

    private suspend fun withLoopbackResponse(
        response: ByteArray,
        onRequest: (String) -> Unit = {},
        block: suspend (String) -> Unit,
    ) {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val serving = thread(start = true) {
            server.accept().use { socket ->
                val request = socket.getInputStream().bufferedReader(StandardCharsets.ISO_8859_1)
                    .lineSequence()
                    .takeWhile { it.isNotEmpty() }
                    .joinToString("\n")
                onRequest(request)
                socket.getOutputStream().use { output ->
                    output.write(response)
                    output.flush()
                }
            }
        }
        try {
            block("http://127.0.0.1:${server.localPort}/subscription")
        } finally {
            server.close()
            serving.join()
        }
    }

    private fun rawResponse(status: Int, headers: Map<String, String>, body: ByteArray): ByteArray {
        val statusText = if (status == 200) "OK" else "Error"
        val headerText = buildString {
            append("HTTP/1.1 $status $statusText\r\n")
            headers.forEach { (name, value) -> append("$name: $value\r\n") }
            append("Content-Length: ${body.size}\r\n")
            append("Connection: close\r\n\r\n")
        }
        return headerText.toByteArray(StandardCharsets.ISO_8859_1) + body
    }
}
