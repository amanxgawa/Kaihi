package app.kaihi.android.core.vpn

enum class XrayEngineState {
    IDLE,
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    ERROR
}

data class XrayTrafficStats(
    val uplinkBytesPerSecond: Long,
    val downlinkBytesPerSecond: Long,
    val uplinkTotalBytes: Long,
    val downlinkTotalBytes: Long,
)

data class XrayLogMessage(
    val message: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    /**
     * Monotonic counter assigned where the line is first read. Log delivery is lossy by design
     * (bounded buffers, a process boundary), so a gap between consecutive received values is how a
     * consumer knows lines went missing instead of silently rendering a hole.
     */
    val sequence: Long = 0,
)
