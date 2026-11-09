package app.kaihi.android.core.vpn.internal

import app.kaihi.android.platform.di.IoDispatcher
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runInterruptible

internal class HttpUrlConnectionTransport @Inject constructor(
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : HttpTransport {

    override suspend fun get(
        url: String,
        requestHeaders: Map<String, String>,
        bodyLimit: Int,
    ): HttpTransportResponse = runInterruptible(ioDispatcher) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            requestHeaders.forEach { (name, value) -> connection.setRequestProperty(name, value) }

            val statusCode = connection.responseCode
            val headers = connection.headerFields
                .mapNotNull { (name, values) -> name?.let { it to values.orEmpty() } }
                .toMap()
            val stream = if (statusCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
                connection.errorStream
            } else {
                connection.inputStream
            }
            val body = stream?.use { readAtMost(it, bodyLimit + 1) } ?: ByteArray(0)
            HttpTransportResponse(statusCode = statusCode, headers = headers, body = body)
        } finally {
            connection.disconnect()
        }
    }

    private fun readAtMost(input: java.io.InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(limit, INITIAL_BUFFER_SIZE))
        val buffer = ByteArray(BUFFER_SIZE)
        var remaining = limit
        while (remaining > 0) {
            val read = input.read(buffer, 0, minOf(buffer.size, remaining))
            if (read < 0) break
            if (read == 0) continue
            output.write(buffer, 0, read)
            remaining -= read
        }
        return output.toByteArray()
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 20_000
        const val BUFFER_SIZE = 8 * 1024
        const val INITIAL_BUFFER_SIZE = 16 * 1024
    }
}
