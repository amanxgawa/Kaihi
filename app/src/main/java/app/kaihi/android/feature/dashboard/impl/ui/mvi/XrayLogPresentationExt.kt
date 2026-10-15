package app.kaihi.android.feature.dashboard.impl.ui.mvi

import app.kaihi.android.core.vpn.XrayLogMessage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

internal fun XrayLogMessage.toDisplayLine(): String {
    val timestamp = TIME_FORMATTER.format(Instant.ofEpochMilli(timestampMillis).atZone(ZoneId.systemDefault()))
    return "[$timestamp] $message"
}
