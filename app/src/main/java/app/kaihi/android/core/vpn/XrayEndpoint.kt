package app.kaihi.android.core.vpn

import app.kaihi.android.core.vpn.spec.XrayProtocol
import app.kaihi.android.core.vpn.spec.XraySecurity
import app.kaihi.android.core.vpn.spec.XrayTransport
import kotlinx.serialization.Serializable

@Serializable
data class XrayEndpoint(
    val address: String,
    val port: Int,
    val protocol: XrayProtocol = XrayProtocol.VLESS,
    // VLESS user id or Trojan password.
    val credential: String,
    // No default: every real endpoint needs real REALITY/TLS settings.
    val security: XraySecurity,
    val transport: XrayTransport = XrayTransport.Xhttp(),
) {
    override fun toString(): String =
        "XrayEndpoint(address=$address, port=$port, protocol=$protocol, " +
            "credential=<redacted>, security=$security, transport=$transport)"
}

/** Same server: protocol, address, port, credential and transport all equal. Security is ignored. */
fun XrayEndpoint.isSameServerAs(other: XrayEndpoint): Boolean =
    protocol == other.protocol && address == other.address && port == other.port &&
        credential == other.credential && transport == other.transport
