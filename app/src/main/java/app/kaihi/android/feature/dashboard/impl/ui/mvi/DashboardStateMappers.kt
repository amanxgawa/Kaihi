package app.kaihi.android.feature.dashboard.impl.ui.mvi

import app.kaihi.android.core.vpn.XrayVpnProfile
import app.kaihi.android.core.vpn.XraySubscriptionSource
import app.kaihi.android.core.vpn.spec.XraySecurity
import app.kaihi.android.core.vpn.spec.XrayTransport
import app.kaihi.android.feature.dashboard.impl.domain.model.ServerLibrary
import app.kaihi.android.feature.dashboard.impl.ui.model.ServerDetailsUi
import app.kaihi.android.feature.dashboard.impl.ui.model.ServerSettingsUi
import app.kaihi.android.feature.dashboard.impl.ui.model.ServerUi
import app.kaihi.android.feature.dashboard.impl.ui.model.SubscriptionUi
import app.kaihi.android.feature.dashboard.impl.ui.model.SubscriptionKind
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

internal fun ServerLibrary.toSubscriptionsUi(): ImmutableList<SubscriptionUi> {
    val selectedId = selectedServer?.id
    val visibleSubscriptions = subscriptions
        .filterNot { it.source == XraySubscriptionSource.Imported && it.servers.isEmpty() }
        .sortedBy { if (it.source == XraySubscriptionSource.Imported) 0 else 1 }
    return visibleSubscriptions.map { subscription ->
        SubscriptionUi(
            id = subscription.id,
            name = subscription.name,
            kind = when (subscription.source) {
                XraySubscriptionSource.Deployed -> SubscriptionKind.DEPLOYED
                XraySubscriptionSource.Imported -> SubscriptionKind.IMPORTED
                is XraySubscriptionSource.Remote -> SubscriptionKind.REMOTE
            },
            isExpanded = subscription.id !in collapsedSubscriptionIds,
            containsSelectedServer = subscription.servers.any { it.id == selectedId },
            servers = subscription.servers.map { it.toServerUi(isSelected = it.id == selectedId) }.toImmutableList(),
        )
    }.toImmutableList()
}

internal fun XrayVpnProfile.toDetailsUi(): ServerDetailsUi {
    val reality = security as? XraySecurity.Reality
    return ServerDetailsUi(
        id = id,
        name = name,
        flag = countryCode?.toFlagEmoji(),
        stack = stackLabel(),
        address = "$serverAddress:$serverPort",
        maskingAs = reality?.serverName ?: (security as? XraySecurity.Tls)?.serverName,
        addedAtMillis = addedAtMillis,
        isDeployed = ssh != null,
    )
}

internal fun XrayVpnProfile.toSettingsUi(): ServerSettingsUi {
    val reality = security as? XraySecurity.Reality
    return ServerSettingsUi(
        id = id,
        name = name,
        fingerprint = reality?.fingerprint,
        muxEnabled = muxEnabled,
        muxConcurrency = muxConcurrency,
        fragmentEnabled = fragmentEnabled,
        isXhttp = transport is XrayTransport.Xhttp,
    )
}

private fun XrayVpnProfile.toServerUi(isSelected: Boolean) = ServerUi(
    id = id,
    name = name,
    stack = stackLabel(),
    flag = countryCode?.toFlagEmoji(),
    isSelected = isSelected,
)
