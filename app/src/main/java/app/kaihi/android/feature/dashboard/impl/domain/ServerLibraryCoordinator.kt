package app.kaihi.android.feature.dashboard.impl.domain

import app.kaihi.android.core.vpn.XraySubscriptionStore
import app.kaihi.android.core.vpn.XrayVpnProfile
import app.kaihi.android.core.vpn.findServer
import app.kaihi.android.core.vpn.realityFingerprint
import app.kaihi.android.core.vpn.spec.XrayFingerprint
import app.kaihi.android.core.vpn.spec.XrayFingerprintSetting
import app.kaihi.android.core.vpn.withServerUpdated
import app.kaihi.android.core.vpn.withSubscriptionUpdated
import app.kaihi.android.core.vpn.withRealityFingerprint
import app.kaihi.android.core.vpn.withoutServer
import app.kaihi.android.core.vpn.withoutSubscription
import app.kaihi.android.feature.dashboard.impl.domain.model.ServerLibrary
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first

internal class ServerLibraryCoordinator @Inject constructor(
    private val subscriptionStore: XraySubscriptionStore,
    private val preferences: ServerListPreferencesRepository,
) {
    val library: Flow<ServerLibrary> =
        combine(subscriptionStore.subscriptions, preferences.preferences) { subscriptions, preferences ->
            ServerLibrary(
                subscriptions = subscriptions,
                selectedServer = preferences.selectedServerId?.let(subscriptions::findServer)
                    ?: subscriptions.firstNotNullOfOrNull { it.servers.firstOrNull() },
                collapsedSubscriptionIds = preferences.collapsedSubscriptionIds,
            )
        }

    suspend fun selectedServer(): XrayVpnProfile? = library.first().selectedServer

    fun select(serverId: String) = preferences.select(serverId)

    fun setCollapsed(subscriptionId: String, collapsed: Boolean) = preferences.setCollapsed(subscriptionId, collapsed)

    suspend fun renameSubscription(subscriptionId: String, name: String) {
        val trimmed = name.trim().takeIf(String::isNotEmpty) ?: return
        subscriptionStore.update { subscriptions ->
            subscriptions.withSubscriptionUpdated(subscriptionId) { it.copy(name = trimmed) }
        }
    }

    suspend fun deleteSubscription(subscriptionId: String) {
        subscriptionStore.update { it.withoutSubscription(subscriptionId) }
        preferences.setCollapsed(subscriptionId, collapsed = false)
    }

    suspend fun forgetServer(serverId: String) {
        subscriptionStore.update { it.withoutServer(serverId) }
    }

    suspend fun setFingerprint(serverId: String, fingerprint: XrayFingerprintSetting) = updateServer(serverId) { server ->
        if (server.realityFingerprint == null) return@updateServer server
        server.withRealityFingerprint { fingerprint }
    }

    suspend fun recordWorkingFingerprint(serverId: String, fingerprint: XrayFingerprint) {
        val current = subscriptionStore.subscriptions.first().findServer(serverId) ?: return
        val auto = current.realityFingerprint as? XrayFingerprintSetting.Auto ?: return
        if (auto.lastWorking == fingerprint) return
        updateServer(serverId) { server ->
            if (server.realityFingerprint !is XrayFingerprintSetting.Auto) return@updateServer server
            server.withRealityFingerprint { setting ->
                if (setting is XrayFingerprintSetting.Auto) setting.copy(lastWorking = fingerprint) else setting
            }
        }
    }

    suspend fun setMuxEnabled(serverId: String, enabled: Boolean) = updateServer(serverId) { it.copy(muxEnabled = enabled) }

    suspend fun setFragmentEnabled(serverId: String, enabled: Boolean) =
        updateServer(serverId) { it.copy(fragmentEnabled = enabled) }

    suspend fun setMuxConcurrency(serverId: String, concurrency: Int) =
        updateServer(serverId) { it.copy(muxConcurrency = concurrency) }

    private suspend fun updateServer(serverId: String, transform: (XrayVpnProfile) -> XrayVpnProfile) {
        subscriptionStore.update { it.withServerUpdated(serverId, transform) }
    }
}
