package app.kaihi.android.feature.wizard.impl.domain.model

import app.kaihi.android.core.vpn.XrayServerProtection

internal data class XrayServerCredentials(
    val uuid: String,
    val publicKey: String,
    val shortId: String,
    val realityServerName: String,
    val countryCode: String? = null,
    val protection: XrayServerProtection? = null,
)
