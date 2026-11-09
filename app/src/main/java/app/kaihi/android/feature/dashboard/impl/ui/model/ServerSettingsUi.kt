package app.kaihi.android.feature.dashboard.impl.ui.model

import androidx.compose.runtime.Immutable
import app.kaihi.android.core.vpn.spec.XrayFingerprintSetting

@Immutable
internal data class ServerSettingsUi(
    val id: String,
    val name: String,
    val fingerprint: XrayFingerprintSetting?,
    val muxEnabled: Boolean,
    val muxConcurrency: Int,
    val fragmentEnabled: Boolean,
    val isXhttp: Boolean,
)
