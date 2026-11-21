package app.kaihi.android.platform.ui.components

import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kaihi.android.R
import app.kaihi.android.platform.ui.theme.KaihiTheme
import app.kaihi.android.platform.ui.theme.dimens
import java.util.Date
import kotlin.math.ceil

enum class UsageLevel { NORMAL, WARNING, ERROR }

@Immutable
data class SubscriptionUsageUi(
    val usedBytes: Long?,
    val totalBytes: Long?,
    val trafficLevel: UsageLevel,
    val expiresAtMillis: Long?,
    val expiryLevel: UsageLevel,
    val announce: String?,
    val refreshFailed: Boolean = false,
)

fun subscriptionUsageUi(
    usedBytes: Long?,
    totalBytes: Long?,
    expiresAtMillis: Long?,
    announce: String?,
    nowMillis: Long,
    refreshFailed: Boolean = false,
): SubscriptionUsageUi? {
    val cleanAnnounce = announce?.takeUnless(String::isBlank)
    if (usedBytes == null && totalBytes == null && expiresAtMillis == null && cleanAnnounce == null && !refreshFailed) {
        return null
    }
    val trafficLevel = when {
        totalBytes == null || totalBytes <= 0L -> UsageLevel.NORMAL
        (usedBytes ?: 0L) >= totalBytes -> UsageLevel.ERROR
        (usedBytes ?: 0L).toDouble() >= totalBytes.toDouble() * 0.9 -> UsageLevel.WARNING
        else -> UsageLevel.NORMAL
    }
    val expiryLevel = when {
        expiresAtMillis == null -> UsageLevel.NORMAL
        expiresAtMillis <= nowMillis -> UsageLevel.ERROR
        expiresAtMillis <= nowMillis + THREE_DAYS_MILLIS -> UsageLevel.WARNING
        else -> UsageLevel.NORMAL
    }
    return SubscriptionUsageUi(
        usedBytes = usedBytes,
        totalBytes = totalBytes,
        trafficLevel = trafficLevel,
        expiresAtMillis = expiresAtMillis,
        expiryLevel = expiryLevel,
        announce = cleanAnnounce,
        refreshFailed = refreshFailed,
    )
}

@Composable
fun SubscriptionUsageCard(
    usage: SubscriptionUsageUi,
    shape: Shape,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
) {
    val dimens = MaterialTheme.dimens
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = containerColor,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = dimens.l, vertical = dimens.m),
            verticalArrangement = Arrangement.spacedBy(dimens.s),
        ) {
            usage.usedBytes?.let { used ->
                val total = usage.totalBytes?.takeIf { it > 0L }
                if (total != null) {
                    val usedText = Formatter.formatShortFileSize(context, used)
                    val totalText = Formatter.formatShortFileSize(context, total)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            text = stringResource(R.string.subscription_usage_traffic_of, usedText, totalText),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                        Text(
                            text = if (usage.trafficLevel == UsageLevel.ERROR) {
                                stringResource(R.string.subscription_usage_traffic_used_up)
                            } else {
                                stringResource(
                                    R.string.subscription_usage_traffic_left,
                                    Formatter.formatShortFileSize(context, (total - used).coerceAtLeast(0L)),
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (usage.trafficLevel == UsageLevel.ERROR) KaihiTheme.status.error else colors.onSurfaceVariant,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (used.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = usage.trafficLevel.color(),
                        trackColor = colors.surfaceContainerHighest,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.subscription_usage_traffic_used, Formatter.formatShortFileSize(context, used)),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            usage.expiresAtMillis?.let { expiresAt ->
                val date = DateFormat.getMediumDateFormat(context).format(Date(expiresAt))
                val color = usage.expiryLevel.color()
                val text = if (usage.expiryLevel == UsageLevel.ERROR) {
                    stringResource(R.string.subscription_usage_expired, date)
                } else {
                    val daysLeft = ceil((expiresAt - System.currentTimeMillis()).toDouble() / MILLIS_PER_DAY)
                        .toInt().coerceAtLeast(1)
                    stringResource(
                        R.string.subscription_usage_active_until,
                        date,
                        pluralStringResource(R.plurals.subscription_usage_days_left, daysLeft, daysLeft),
                    )
                }
                Text(text = text, style = MaterialTheme.typography.bodySmall, color = color)
            }

            usage.announce?.let { announce ->
                Text(
                    text = announce,
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawLine(
                                color = colors.outline,
                                start = Offset(0f, 0f),
                                end = Offset(0f, size.height),
                                strokeWidth = 2.dp.toPx(),
                            )
                        }
                        .padding(start = dimens.m),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurface,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (usage.refreshFailed) {
                Text(
                    text = stringResource(R.string.subscription_usage_refresh_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun UsageLevel.color(): Color = when (this) {
    UsageLevel.NORMAL -> MaterialTheme.colorScheme.primary
    UsageLevel.WARNING -> KaihiTheme.status.warning
    UsageLevel.ERROR -> KaihiTheme.status.error
}

private const val MILLIS_PER_DAY = 86_400_000.0
private const val THREE_DAYS_MILLIS = 3L * 86_400_000L
