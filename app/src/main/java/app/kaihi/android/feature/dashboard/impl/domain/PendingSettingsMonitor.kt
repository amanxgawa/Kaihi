package app.kaihi.android.feature.dashboard.impl.domain

import app.kaihi.android.core.vpn.TunnelSettingsSource
import app.kaihi.android.core.vpn.XrayServiceProvider
import app.kaihi.android.core.vpn.configfactory.XrayClientConfigFactory
import app.kaihi.android.core.vpn.findServer
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

internal class PendingSettingsMonitor @Inject constructor(
    private val serviceProvider: XrayServiceProvider,
    private val tunnelSettingsSource: TunnelSettingsSource,
    private val coordinator: ServerLibraryCoordinator,
) {

    val pendingServerId: Flow<String?> = combine(
        serviceProvider.monitoring.state,
        serviceProvider.monitoring.sessionServerId,
        serviceProvider.monitoring.sessionSettingsKey,
        coordinator.library,
        tunnelSettingsSource.settings,
    ) { engineState, serverId, settingsKey, library, settings ->
        reconnectToApplyServerId(
            engineState = engineState,
            sessionServerId = serverId,
            sessionSettingsKey = settingsKey,
            sessionServer = serverId?.let { library.subscriptions.findServer(it) },
            settings = settings,
        )
    }.distinctUntilChanged()

    suspend fun restart(serverId: String): Result<Unit> {
        val server = coordinator.library.first().subscriptions.findServer(serverId)
            ?: return Result.failure(UnknownServerException(serverId))
        val config = XrayClientConfigFactory.createRuntimeConfig(server, tunnelSettingsSource.current())
        return serviceProvider.control.start(config)
    }

    private class UnknownServerException(serverId: String) : IllegalStateException(
        "Cannot restart unknown server: $serverId",
    )
}
