package app.kaihi.android.core.vpn.spec

import kotlinx.serialization.Serializable

@Serializable
sealed interface XrayTransport {
    @Serializable
    data class Xhttp(val path: String = "/", val mode: XhttpMode = XhttpMode.STREAM_ONE) : XrayTransport

    @Serializable
    data class Raw(val flow: XrayFlow? = null) : XrayTransport

    @Serializable
    data class WebSocket(val path: String = "/", val host: String? = null) : XrayTransport

    @Serializable
    data class HttpUpgrade(val path: String = "/", val host: String? = null) : XrayTransport

    @Serializable
    data class Grpc(
        val serviceName: String = "",
        val multiMode: Boolean = false,
        val authority: String? = null,
    ) : XrayTransport
}

@Serializable
enum class XrayFlow(val wireValue: String) {
    VISION("xtls-rprx-vision"),
}
