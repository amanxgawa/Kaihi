package app.kaihi.android.core.vpn

import app.kaihi.android.core.vpn.spec.XrayProtocol
import app.kaihi.android.core.vpn.spec.XraySecurity
import app.kaihi.android.core.vpn.spec.XrayTransport

internal fun testEndpoint(
    address: String = "proxy.example",
    port: Int = 443,
    protocol: XrayProtocol = XrayProtocol.VLESS,
    credential: String = "123e4567-e89b-12d3-a456-426614174000",
    security: XraySecurity = XraySecurity.Reality(
        serverName = "www.example.com",
        publicKey = "public-key",
        shortId = "abcd",
    ),
    transport: XrayTransport = XrayTransport.Xhttp(),
) = XrayEndpoint(
    address = address,
    port = port,
    protocol = protocol,
    credential = credential,
    security = security,
    transport = transport,
)

internal fun XrayVpnProfile.copyEndpoints(transform: (XrayEndpoint) -> XrayEndpoint): XrayVpnProfile =
    copy(endpoints = endpoints.map(transform))

internal val XrayVpnProfile.firstEndpoint: XrayEndpoint
    get() = endpoints.first()
