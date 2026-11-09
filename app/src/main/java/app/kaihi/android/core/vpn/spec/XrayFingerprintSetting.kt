package app.kaihi.android.core.vpn.spec

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
sealed interface XrayFingerprintSetting {
    /** Probe on every connect; [lastWorking] only decides which fingerprint is tried first. */
    @Serializable
    @SerialName("auto")
    data class Auto(val lastWorking: XrayFingerprint? = null) : XrayFingerprintSetting

    @Serializable
    @SerialName("fixed")
    data class Fixed(val fingerprint: XrayFingerprint) : XrayFingerprintSetting
}

/**
 * Firefox and Safari lead because this pinned Xray version rejects Edge, iOS, and Android.
 * Chrome follows because DPI blocks it on some networks.
 */
private val AUTO_ORDER = listOf(
    XrayFingerprint.FIREFOX,
    XrayFingerprint.SAFARI,
    XrayFingerprint.CHROME,
    XrayFingerprint.EDGE,
    XrayFingerprint.IOS,
    XrayFingerprint.ANDROID,
)

fun XrayFingerprintSetting.candidates(): List<XrayFingerprint> = when (this) {
    is XrayFingerprintSetting.Fixed -> listOf(fingerprint)
    is XrayFingerprintSetting.Auto -> buildList {
        lastWorking?.let(::add)
        AUTO_ORDER.forEach { candidate -> if (candidate !in this) add(candidate) }
    }
}

fun XrayFingerprintSetting.preferred(): XrayFingerprint = candidates().first()
