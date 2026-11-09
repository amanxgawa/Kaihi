package app.kaihi.android.core.vpn.internal

import app.kaihi.android.core.vpn.XrayEngineState
import app.kaihi.android.core.vpn.XrayFailure
import app.kaihi.android.core.vpn.XrayLogMessage
import app.kaihi.android.core.vpn.XrayTrafficStats

internal sealed interface TelemetryEvent {
    data class State(val state: XrayEngineState) : TelemetryEvent
    data class Stats(val stats: XrayTrafficStats) : TelemetryEvent
    data class Log(val log: XrayLogMessage) : TelemetryEvent
    data object HealthDegraded : TelemetryEvent
    data class SessionServer(
        val serverId: String?,
        val settingsKey: String? = null,
        val fingerprint: String? = null,
    ) : TelemetryEvent
    data class RunningSince(val runningSinceRealtime: Long?) : TelemetryEvent
    data class Failure(val failure: XrayFailure?) : TelemetryEvent
}
