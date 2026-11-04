package app.kaihi.android.core.vpn.internal

import app.kaihi.android.core.vpn.SocksEndpoint

internal object HevTunnelConfigFactory {

    private const val MAX_PORT = 65_535

    fun build(socksEndpoint: SocksEndpoint, ipv6Enabled: Boolean): String {
        require(socksEndpoint.host.isNotBlank()) { "Xray SOCKS host must not be blank." }
        require(socksEndpoint.port in 1..MAX_PORT) { "Invalid Xray SOCKS port: ${socksEndpoint.port}" }

        val lines = buildList {
            add("tunnel:")
            add("  name: kaihi")
            add("  mtu: ${VpnTunnelDefaults.VPN_MTU}")
            add("  ipv4: ${VpnTunnelDefaults.VPN_ADDRESS}")
            if (ipv6Enabled) {
                add("  ipv6: '${VpnTunnelDefaults.VPN_ADDRESS_V6}'")
            }
            add("socks5:")
            add("  address: ${socksEndpoint.host}")
            add("  port: ${socksEndpoint.port}")
            add("  udp: 'udp'")
            if (socksEndpoint.username != null && socksEndpoint.password != null) {
                add("  username: '${socksEndpoint.username.yamlSingleQuoted()}'")
                add("  password: '${socksEndpoint.password.yamlSingleQuoted()}'")
            }
            add("misc:")
            add("  log-level: warn")
        }
        return lines.joinToString("\n")
    }

    // YAML single-quoted scalars need an embedded quote doubled, not backslash-escaped.
    private fun String.yamlSingleQuoted(): String = replace("'", "''")
}
