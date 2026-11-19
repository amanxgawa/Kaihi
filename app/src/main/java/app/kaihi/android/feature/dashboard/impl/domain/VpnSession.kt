package app.kaihi.android.feature.dashboard.impl.domain

import app.kaihi.android.core.vpn.XrayEngineState
import app.kaihi.android.core.vpn.XrayFailure
import app.kaihi.android.core.vpn.XrayLogMessage
import app.kaihi.android.core.vpn.XrayServiceProvider
import app.kaihi.android.core.vpn.XraySessionLauncher
import app.kaihi.android.core.vpn.XrayTrafficStats
import app.kaihi.android.core.vpn.XrayVpnProfile
import app.kaihi.android.core.vpn.spec.XrayFingerprint
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The dashboard's view of the tunnel: what `:vpn` reports, and starting or stopping a session. */
internal class VpnSession @Inject constructor(
    private val serviceProvider: XrayServiceProvider,
    private val launcher: XraySessionLauncher,
) {
    val state: StateFlow<XrayEngineState> get() = serviceProvider.monitoring.state
    val stats: Flow<XrayTrafficStats> get() = serviceProvider.monitoring.stats
    val logs: Flow<XrayLogMessage> get() = serviceProvider.monitoring.logs
    val healthDegraded: Flow<Unit> get() = serviceProvider.monitoring.healthDegraded
    val sessionServerId: StateFlow<String?> get() = serviceProvider.monitoring.sessionServerId
    val sessionFingerprint: StateFlow<XrayFingerprint?> get() = serviceProvider.monitoring.sessionFingerprint
    val runningSinceRealtime: StateFlow<Long?> get() = serviceProvider.monitoring.runningSinceRealtime
    val failure: StateFlow<XrayFailure?> get() = serviceProvider.monitoring.failure

    suspend fun start(server: XrayVpnProfile): Result<Unit> = launcher.start(server)
    suspend fun stop(): Result<Unit> = serviceProvider.control.stop()
}
