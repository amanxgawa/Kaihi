package app.kaihi.android.feature.wizard.impl.domain.model

import app.kaihi.android.core.vpn.XrayProtectionState
import app.kaihi.android.core.vpn.XrayServerProtection

internal sealed interface ProtectionPlan {
    data class Skip(val protection: XrayServerProtection) : ProtectionPlan
    data object Run : ProtectionPlan
}

internal fun protectionPlan(protectServer: Boolean, packageManager: PackageManager): ProtectionPlan = when {
    !protectServer -> ProtectionPlan.Skip(
        XrayServerProtection(XrayProtectionState.OFF, XrayProtectionState.OFF),
    )
    packageManager != PackageManager.APT -> ProtectionPlan.Skip(
        XrayServerProtection(XrayProtectionState.UNSUPPORTED, XrayProtectionState.UNSUPPORTED),
    )
    else -> ProtectionPlan.Run
}
