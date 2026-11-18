package app.kaihi.android.feature.dashboard.impl.ui.mvi

import app.kaihi.android.core.vpn.XrayVpnProfile
import app.kaihi.android.core.vpn.spec.XraySecurity
import app.kaihi.android.core.vpn.spec.XrayTransport

internal fun XrayVpnProfile.stackLabel(): String = listOfNotNull(
    "VLESS",
    when (transport) {
        is XrayTransport.Xhttp -> "XHTTP"
        is XrayTransport.Raw -> "RAW"
    },
    (transport as? XrayTransport.Raw)?.flow?.let { "VISION" },
    when (security) {
        is XraySecurity.Reality -> "REALITY"
        is XraySecurity.Tls -> "TLS"
    },
).joinToString(separator = " · ")
