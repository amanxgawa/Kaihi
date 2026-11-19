package app.kaihi.android.core.vpn

import java.nio.charset.StandardCharsets

internal class FakeHttpTransport(vararg responses: HttpTransportResponse) : HttpTransport {
    private val responses = ArrayDeque<Result<HttpTransportResponse>>()
    val requestedUrls = mutableListOf<String>()
    val requestHeaders = mutableListOf<Map<String, String>>()

    init {
        responses.forEach(::enqueue)
    }

    fun enqueue(response: HttpTransportResponse) {
        responses += Result.success(response)
    }

    fun enqueueFailure(error: Throwable) {
        responses += Result.failure(error)
    }

    override suspend fun get(
        url: String,
        requestHeaders: Map<String, String>,
        bodyLimit: Int,
    ): HttpTransportResponse {
        requestedUrls += url
        this.requestHeaders += requestHeaders
        return responses.removeFirst().getOrThrow()
    }

    companion object {
        fun ok(
            body: String,
            headers: Map<String, List<String>> = emptyMap(),
        ): HttpTransportResponse = response(200, headers, body)

        fun response(
            statusCode: Int,
            headers: Map<String, List<String>> = emptyMap(),
            body: String = "",
        ): HttpTransportResponse = response(
            statusCode = statusCode,
            headers = headers,
            body = body.toByteArray(StandardCharsets.UTF_8),
        )

        fun response(
            statusCode: Int,
            headers: Map<String, List<String>> = emptyMap(),
            body: ByteArray,
        ): HttpTransportResponse = HttpTransportResponse(statusCode, headers, body)

        fun response(
            statusCode: Int,
            headers: Map<String, String>,
        ): HttpTransportResponse = HttpTransportResponse(
            statusCode = statusCode,
            headers = headers.mapValues { listOf(it.value) },
            body = ByteArray(0),
        )
    }
}
