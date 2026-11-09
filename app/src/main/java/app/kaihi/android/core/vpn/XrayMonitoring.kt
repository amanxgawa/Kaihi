package app.kaihi.android.core.vpn

import app.kaihi.android.core.vpn.spec.XrayFingerprint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface XrayMonitoring {
    val state: StateFlow<XrayEngineState>
    val stats: Flow<XrayTrafficStats>
    val logs: Flow<XrayLogMessage>
    val healthDegraded: Flow<Unit>

    /** Null while no session runs. Comes from `:vpn`, so it survives the UI process being recreated underneath a live tunnel. */
    val sessionServerId: StateFlow<String?>

    /** The `XrayClientConfigFactory.settingsKey` of the running session, null while no session runs or when the `:vpn` process predates this field. */
    val sessionSettingsKey: StateFlow<String?>

    /** The uTLS fingerprint the running session uses; null while no session runs. */
    val sessionFingerprint: StateFlow<XrayFingerprint?>

    /** [android.os.SystemClock.elapsedRealtime] when the current session reached RUNNING, null
     *  while no session runs. Comes from `:vpn` for the same reason as [sessionServerId]. */
    val runningSinceRealtime: StateFlow<Long?>

    /** The current Xray startup failure, or null when no failure has been reported. */
    val failure: StateFlow<XrayFailure?>
}
