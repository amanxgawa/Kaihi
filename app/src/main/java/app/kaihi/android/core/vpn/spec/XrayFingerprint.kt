package app.kaihi.android.core.vpn.spec

import kotlinx.serialization.Serializable

@Serializable
enum class XrayFingerprint(val wireValue: String) {
    EDGE("edge"),
    CHROME("chrome"),
    FIREFOX("firefox"),
    SAFARI("safari"),
    IOS("ios"),
    ANDROID("android");

    companion object {
        fun fromWireValue(value: String?): XrayFingerprint? =
            value?.let { v -> entries.firstOrNull { it.wireValue == v } }
    }
}
