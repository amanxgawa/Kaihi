package app.kaihi.android.feature.dashboard.impl.domain

import app.kaihi.android.core.vpn.XrayEngineState
import app.kaihi.android.core.vpn.XrayFailure
import app.kaihi.android.core.vpn.XrayLogMessage
import app.kaihi.android.core.vpn.XrayControl
import app.kaihi.android.core.vpn.XrayMonitoring
import app.kaihi.android.core.vpn.XrayTrafficStats
import app.kaihi.android.core.vpn.TunnelSettingsSource
import app.kaihi.android.core.vpn.XrayVpnProfile
import app.kaihi.android.core.vpn.start
import app.kaihi.android.core.vpn.spec.XrayFingerprint
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The dashboard's view of the tunnel: what `:vpn` reports, and starting or stopping a session. */
internal class VpnSession @Inject constructor(
    private val monitoring: XrayMonitoring,
    private val control: XrayControl,
    private val tunnelSettingsSource: TunnelSettingsSource,
) {
    val state: StateFlow<XrayEngineState> get() = monitoring.state
    val stats: Flow<XrayTrafficStats> get() = monitoring.stats
    val logs: Flow<XrayLogMessage> get() = monitoring.logs
    val healthDegraded: Flow<Unit> get() = monitoring.healthDegraded
    val sessionServerId: StateFlow<String?> get() = monitoring.sessionServerId
    val sessionFingerprint: StateFlow<XrayFingerprint?> get() = monitoring.sessionFingerprint
    val runningSinceRealtime: StateFlow<Long?> get() = monitoring.runningSinceRealtime
    val failure: StateFlow<XrayFailure?> get() = monitoring.failure

    fun needsVpnPermission(): Boolean = control.needsVpnPermission()

    suspend fun start(server: XrayVpnProfile): Result<Unit> = control.start(server, tunnelSettingsSource.current())
    suspend fun stop(): Result<Unit> = control.stop()
}
