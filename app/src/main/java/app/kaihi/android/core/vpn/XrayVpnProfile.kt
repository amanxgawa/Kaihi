package app.kaihi.android.core.vpn

import app.kaihi.android.core.vpn.spec.XrayProtocol
import app.kaihi.android.core.vpn.spec.XraySecurity
import app.kaihi.android.core.vpn.spec.XrayTransport
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XrayVpnProfile(
    val id: String,
    val name: String,
    val ssh: XraySshAccess? = null,
    val serverAddress: String,
    val serverPort: Int,
    val protocol: XrayProtocol = XrayProtocol.VLESS,
    @SerialName("uuid") val credential: String,
    // No default: every real profile needs real REALITY key material from a deployment.
    val security: XraySecurity,
    val transport: XrayTransport = XrayTransport.Xhttp(),
    val addedAtMillis: Long,
    // ISO 3166-1 alpha-2.
    val countryCode: String? = null,
    val muxEnabled: Boolean = false,
    val muxConcurrency: Int = DEFAULT_MUX_CONCURRENCY,
    val fragmentEnabled: Boolean = false,
    val serverProtection: XrayServerProtection? = null,
) {
    override fun toString(): String =
        "XrayVpnProfile(id=$id, name=$name, ssh=$ssh, serverAddress=$serverAddress, " +
            "serverPort=$serverPort, protocol=$protocol, credential=<redacted>, security=$security, " +
            "transport=$transport, addedAtMillis=$addedAtMillis, countryCode=$countryCode, " +
            "muxEnabled=$muxEnabled, muxConcurrency=$muxConcurrency, fragmentEnabled=$fragmentEnabled, " +
            "serverProtection=$serverProtection)"

    companion object {
        const val DEFAULT_MUX_CONCURRENCY = 8
    }
}

@Serializable
data class XraySshAccess(
    val host: String,
    val port: Int,
    val username: String,
    val expectedFingerprint: String? = null,
) {
    override fun toString(): String =
        "XraySshAccess(host=$host, port=$port, username=$username)"
}
