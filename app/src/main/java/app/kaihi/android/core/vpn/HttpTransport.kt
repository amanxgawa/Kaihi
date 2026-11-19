package app.kaihi.android.core.vpn

interface HttpTransport {
    suspend fun get(
        url: String,
        requestHeaders: Map<String, String>,
        bodyLimit: Int,
    ): HttpTransportResponse
}

data class HttpTransportResponse(
    val statusCode: Int,
    val headers: Map<String, List<String>>,
    val body: ByteArray,
)
