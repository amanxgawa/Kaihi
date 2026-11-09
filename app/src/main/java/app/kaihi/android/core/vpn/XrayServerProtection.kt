package app.kaihi.android.core.vpn

import kotlinx.serialization.Serializable

@Serializable
enum class XrayProtectionState { ENABLED, OFF, UNSUPPORTED, FAILED }

@Serializable
data class XrayServerProtection(
    val bruteForceGuard: XrayProtectionState,
    val securityUpdates: XrayProtectionState,
)
