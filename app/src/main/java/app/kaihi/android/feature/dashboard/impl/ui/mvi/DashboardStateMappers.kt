package app.kaihi.android.feature.dashboard.impl.ui.mvi

import app.kaihi.android.core.vpn.XrayVpnProfile
import app.kaihi.android.core.vpn.XraySubscriptionSource
import app.kaihi.android.core.vpn.XraySubscription
import app.kaihi.android.core.vpn.XraySubscriptionUserInfo
import app.kaihi.android.core.vpn.spec.XraySecurity
import app.kaihi.android.core.vpn.spec.XrayTransport
import app.kaihi.android.feature.dashboard.impl.domain.model.ServerLibrary
import app.kaihi.android.feature.dashboard.impl.ui.model.ServerDetailsUi
import app.kaihi.android.feature.dashboard.impl.ui.model.ServerSettingsUi
import app.kaihi.android.feature.dashboard.impl.ui.model.ServerUi
import app.kaihi.android.feature.dashboard.impl.ui.model.RemoteSubscriptionUi
import app.kaihi.android.feature.dashboard.impl.ui.model.SubscriptionEditorUi
import app.kaihi.android.feature.dashboard.impl.ui.model.SubscriptionUi
import app.kaihi.android.feature.dashboard.impl.ui.model.SubscriptionKind
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

internal fun ServerLibrary.toSubscriptionsUi(
    refreshingIds: Set<String> = emptySet(),
    failedRefreshIds: Set<String> = emptySet(),
): ImmutableList<SubscriptionUi> {
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
            remote = (subscription.source as? XraySubscriptionSource.Remote)?.let { remote ->
                val userInfo = remote.userInfo
                RemoteSubscriptionUi(
                    usedBytes = userInfo?.usedBytes(),
                    totalBytes = userInfo?.totalBytes,
                    expireEpochSeconds = userInfo?.expireEpochSeconds,
                )
            },
            isRefreshing = subscription.id in refreshingIds,
            refreshFailed = subscription.id in failedRefreshIds,
        )
    }.toImmutableList()
}

internal fun XraySubscription.toEditorUi(): SubscriptionEditorUi {
    val remote = source as? XraySubscriptionSource.Remote ?: return SubscriptionEditorUi(id = id, name = name)
    return SubscriptionEditorUi(
        id = id,
        name = name,
        usedBytes = remote.userInfo?.usedBytes(),
        totalBytes = remote.userInfo?.totalBytes,
        expireEpochSeconds = remote.userInfo?.expireEpochSeconds,
        updateIntervalHours = remote.updateIntervalHours,
        lastUpdatedMillis = remote.lastUpdatedMillis,
        announce = remote.announce,
        supportUrl = remote.supportUrl,
        webPageUrl = remote.webPageUrl,
    )
}

private fun XraySubscriptionUserInfo.usedBytes(): Long? {
    if (uploadBytes == null && downloadBytes == null) return null
    val upload = uploadBytes ?: 0L
    val download = downloadBytes ?: 0L
    return if (Long.MAX_VALUE - upload < download) Long.MAX_VALUE else upload + download
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
