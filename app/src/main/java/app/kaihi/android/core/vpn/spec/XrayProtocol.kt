package app.kaihi.android.core.vpn.spec

import kotlinx.serialization.Serializable

@Serializable
enum class XrayProtocol {
    VLESS,
    TROJAN,
}
