package app.kaihi.android.core.vpn.spec

import kotlinx.serialization.Serializable

@Serializable
sealed interface XraySecurity {
    @Serializable
    data class Reality(
        val serverName: String,
        val publicKey: String,
        val shortId: String,
        val fingerprint: XrayFingerprintSetting = XrayFingerprintSetting.Auto(),
    ) : XraySecurity

    @Serializable
    data class Tls(
        val serverName: String,
        val allowInsecure: Boolean = false,
    ) : XraySecurity
}
