package app.kaihi.android.feature.dashboard.impl.ui.mvi

import app.kaihi.android.core.vpn.XrayEndpoint
import app.kaihi.android.core.vpn.XrayVpnProfile
import app.kaihi.android.core.vpn.spec.XrayProtocol
import app.kaihi.android.core.vpn.spec.XraySecurity
import app.kaihi.android.core.vpn.spec.XrayTransport

internal fun XrayVpnProfile.stackLabel(): String = endpoints
    .map { it.stackLabel() }
    .distinct()
    .joinToString(separator = " / ")
    .let { label ->
        if (endpoints.size > 1) "$label · ×${endpoints.size}" else label
    }

private fun XrayEndpoint.stackLabel(): String = listOfNotNull(
    when (protocol) {
        XrayProtocol.VLESS -> "VLESS"
        XrayProtocol.TROJAN -> "Trojan"
    },
    when (transport) {
        is XrayTransport.Xhttp -> "XHTTP"
        is XrayTransport.Raw -> "RAW"
        is XrayTransport.WebSocket -> "WebSocket"
        is XrayTransport.HttpUpgrade -> "HTTPUpgrade"
        is XrayTransport.Grpc -> "gRPC"
    },
    (transport as? XrayTransport.Raw)?.flow?.let { "VISION" },
    when (security) {
        is XraySecurity.Reality -> "REALITY"
        is XraySecurity.Tls -> "TLS"
    },
).joinToString(separator = " · ")
