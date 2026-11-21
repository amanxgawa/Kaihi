package app.kaihi.android.core.vpn

import app.kaihi.android.core.vpn.spec.XraySecurity
import app.kaihi.android.core.vpn.spec.XrayTransport
import app.kaihi.android.core.vpn.spec.XrayProtocol
import app.kaihi.android.core.vpn.spec.preferred
import java.net.URLEncoder

fun XrayVpnProfile.toShareUri(): String = buildString {
    val isVless = protocol == XrayProtocol.VLESS
    append(if (isVless) "vless://" else "trojan://")
    append(if (isVless) credential else credential.urlEncode())
    append('@')
    append(serverAddress)
    append(':')
    append(serverPort)
    append(if (isVless) "?encryption=none" else "?")

    var firstQueryParameter = !isVless
    fun parameter(name: String, value: String) {
        if (!firstQueryParameter) append('&')
        append(name).append('=').append(value.urlEncode())
        firstQueryParameter = false
    }

    when (val security = security) {
        is XraySecurity.Reality -> {
            parameter("security", "reality")
            parameter("sni", security.serverName)
            parameter("fp", security.fingerprint.preferred().wireValue)
            parameter("pbk", security.publicKey)
            parameter("sid", security.shortId)
        }
        is XraySecurity.Tls -> {
            parameter("security", "tls")
            parameter("sni", security.serverName)
            if (security.allowInsecure) parameter("allowInsecure", "1")
        }
    }

    when (val transport = transport) {
        is XrayTransport.Xhttp -> {
            parameter("type", "xhttp")
            parameter("path", transport.path)
            parameter("mode", transport.mode.wireValue)
            val host = (security as? XraySecurity.Reality)?.serverName
                ?: (security as? XraySecurity.Tls)?.serverName
            host?.let { parameter("host", it) }
        }
        is XrayTransport.Raw -> {
            parameter("type", "raw")
            if (isVless) transport.flow?.let { flow -> parameter("flow", flow.wireValue) }
        }
        is XrayTransport.WebSocket -> {
            parameter("type", "ws")
            parameter("path", transport.path)
            transport.host?.let { parameter("host", it) }
        }
        is XrayTransport.HttpUpgrade -> {
            parameter("type", "httpupgrade")
            parameter("path", transport.path)
            transport.host?.let { parameter("host", it) }
        }
        is XrayTransport.Grpc -> {
            parameter("type", "grpc")
            if (transport.serviceName.isNotEmpty()) parameter("serviceName", transport.serviceName)
            if (transport.multiMode) parameter("mode", "multi")
            transport.authority?.let { parameter("authority", it) }
        }
    }

    append('#').append(name.urlEncode())
}

fun XraySubscription.toShareUriList(): String = servers.joinToString(separator = "\n") { it.toShareUri() }

private fun String.urlEncode(): String = URLEncoder.encode(this, "UTF-8").replace("+", "%20")
