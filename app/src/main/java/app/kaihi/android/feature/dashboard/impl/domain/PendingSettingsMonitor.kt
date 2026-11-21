package app.kaihi.android.feature.dashboard.impl.domain

import app.kaihi.android.core.vpn.TunnelSettingsSource
import app.kaihi.android.core.vpn.XrayControl
import app.kaihi.android.core.vpn.XrayMonitoring
import app.kaihi.android.core.vpn.findServer
import app.kaihi.android.core.vpn.start
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first

internal class PendingSettingsMonitor @Inject constructor(
    private val monitoring: XrayMonitoring,
    private val control: XrayControl,
    private val tunnelSettingsSource: TunnelSettingsSource,
    private val coordinator: ServerLibraryCoordinator,
) {

    val pendingServerId: Flow<String?> = combine(
        monitoring.state,
        monitoring.sessionServerId,
        monitoring.sessionSettingsKey,
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
        return control.start(server, tunnelSettingsSource.current())
    }

    private class UnknownServerException(serverId: String) : IllegalStateException(
        "Cannot restart unknown server: $serverId",
    )
}
