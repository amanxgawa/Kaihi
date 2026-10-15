package app.kaihi.android.core.vpn.spec

import kotlinx.serialization.Serializable

@Serializable
enum class XrayFingerprint(val wireValue: String) {
    EDGE("edge"),
    CHROME("chrome"),
    FIREFOX("firefox"),
    SAFARI("safari"),
    IOS("ios"),
    ANDROID("android"),
    RANDOM("random"),
    RANDOMIZED("randomized"),
    UNSAFE("unsafe"),
}
