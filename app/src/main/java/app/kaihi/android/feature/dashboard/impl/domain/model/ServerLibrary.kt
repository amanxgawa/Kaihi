package app.kaihi.android.feature.dashboard.impl.domain.model

import app.kaihi.android.core.vpn.XraySubscription
import app.kaihi.android.core.vpn.XrayVpnProfile

internal data class ServerLibrary(
    val subscriptions: List<XraySubscription>,
    val selectedServer: XrayVpnProfile?,
    val collapsedSubscriptionIds: Set<String>,
)
