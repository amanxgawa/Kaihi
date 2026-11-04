package app.kaihi.android.core.vpn.configfactory

import app.kaihi.android.core.vpn.spec.InboundObject
import app.kaihi.android.core.vpn.spec.LogObject
import app.kaihi.android.core.vpn.spec.OutboundObject
import app.kaihi.android.core.vpn.spec.RealityObject
import app.kaihi.android.core.vpn.spec.VlessClient
import app.kaihi.android.core.vpn.spec.VlessInboundSettings
import app.kaihi.android.core.vpn.spec.XrayConfig
import app.kaihi.android.core.vpn.spec.toJsonObject

object XrayServerConfigFactory {
    const val DEFAULT_SERVER_PORT = 443
    const val IPV4_ANY = "0.0.0.0"
    // Linux treats "::" as dual-stack, but binding it fails when the kernel has no IPv6 support.
    const val DUAL_STACK_ANY = "::"

    // The port REALITY dials on the site it impersonates. Not serverPort: that is where our own
    // inbound listens, and the masking host answers TLS on 443 regardless of what we picked.
    private const val REALITY_TARGET_PORT = 443

    fun build(
        uuid: String,
        privateKey: String,
        shortId: String,
        serverPort: Int = DEFAULT_SERVER_PORT,
        realityServerName: String,
        listenAddress: String = IPV4_ANY,
    ): XrayConfig = XrayConfig(
        log = LogObject(loglevel = "warning"),
        inbounds = listOf(
            InboundObject(
                listen = listenAddress,
                port = intPrimitive(serverPort),
                protocol = "vless",
                settings = VlessInboundSettings(
                    clients = listOf(VlessClient(id = uuid)),
                    decryption = "none",
                ).toJsonObject(),
                streamSettings = XrayTransportFactory.serverXhttpRealityStreamSettings(
                    RealityObject(
                        show = false,
                        target = "$realityServerName:$REALITY_TARGET_PORT",
                        xver = 0,
                        serverNames = listOf(realityServerName),
                        privateKey = privateKey,
                        shortIds = listOf(shortId),
                    ),
                ),
            ),
        ),
        outbounds = listOf(OutboundObject(protocol = "freedom", tag = "direct")),
    )
}
