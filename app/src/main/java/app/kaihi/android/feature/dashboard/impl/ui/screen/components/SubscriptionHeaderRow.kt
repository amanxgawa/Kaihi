package app.kaihi.android.feature.dashboard.impl.ui.screen.components

import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import app.kaihi.android.R
import app.kaihi.android.feature.dashboard.impl.ui.model.SubscriptionUi
import app.kaihi.android.feature.dashboard.impl.ui.model.SubscriptionKind
import app.kaihi.android.platform.ui.icons.KaihiIcons
import app.kaihi.android.platform.ui.theme.size.dimens
import java.util.Date

private const val EXPANDED_CHEVRON_ROTATION = 180f

@Composable
internal fun SubscriptionHeaderRow(
    subscription: SubscriptionUi,
    onToggleExpanded: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = MaterialTheme.dimens
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val isHighlighted = !subscription.isExpanded && subscription.containsSelectedServer

    val containerColor by animateColorAsState(
        targetValue = if (isHighlighted) colors.primaryContainer else colors.surfaceContainerLow,
        animationSpec = dashboardEffectsSpec(),
        label = "subscriptionHeaderContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (isHighlighted) colors.onPrimaryContainer else colors.onSurface,
        animationSpec = dashboardEffectsSpec(),
        label = "subscriptionHeaderContent",
    )
    val bottomCorner by animateDpAsState(
        targetValue = if (subscription.isExpanded) SegmentInnerCorner else SegmentOuterCorner,
        animationSpec = dashboardSpatialSpec(),
        label = "subscriptionHeaderBottomCorner",
    )
    val chevronRotation by animateFloatAsState(
        targetValue = if (subscription.isExpanded) EXPANDED_CHEVRON_ROTATION else 0f,
        animationSpec = dashboardSpatialSpec(),
        label = "subscriptionHeaderChevron",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            onClick = onToggleExpanded,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(
                topStart = SegmentOuterCorner,
                topEnd = SegmentOuterCorner,
                bottomEnd = bottomCorner,
                bottomStart = bottomCorner,
            ),
            color = containerColor,
            contentColor = contentColor,
        ) {
            Row(
                modifier = Modifier
                    .heightIn(min = dimens.xxl + dimens.m)
                    .padding(start = dimens.l, end = dimens.s),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.l),
            ) {
                Box(modifier = Modifier.size(dimens.control), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = KaihiIcons.Outlined.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.graphicsLayer { rotationZ = chevronRotation },
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (subscription.kind == SubscriptionKind.IMPORTED) {
                            stringResource(R.string.dashboard_subscription_imported_name)
                        } else {
                            subscription.name
                        },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.dashboard_subscription_own_servers, subscription.servers.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                    )
                }

                if (subscription.kind != SubscriptionKind.IMPORTED) {
                    Row {
                        if (subscription.kind == SubscriptionKind.REMOTE) {
                            IconButton(onClick = onRefresh, enabled = !subscription.isRefreshing) {
                                if (subscription.isRefreshing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(dimens.icon),
                                        strokeWidth = dimens.xs,
                                    )
                                } else {
                                    Icon(
                                        imageVector = KaihiIcons.Outlined.Refresh,
                                        contentDescription = stringResource(
                                            R.string.dashboard_subscription_refresh_cd,
                                            subscription.name,
                                        ),
                                    )
                                }
                            }
                        }
                        IconButton(onClick = onShare) {
                            Icon(
                                imageVector = KaihiIcons.Outlined.Share,
                                contentDescription = stringResource(R.string.dashboard_subscription_share_cd, subscription.name),
                            )
                        }
                        IconButton(onClick = onEdit) {
                            Icon(
                                imageVector = KaihiIcons.Outlined.Edit,
                                contentDescription = stringResource(R.string.dashboard_subscription_edit_cd, subscription.name),
                            )
                        }
                    }
                }
            }
        }

        subscription.remote?.let { remote ->
            val used = remote.usedBytes?.let { Formatter.formatShortFileSize(context, it) }
            val total = remote.totalBytes?.let { Formatter.formatShortFileSize(context, it) }
            val traffic = when {
                used != null && total != null -> stringResource(R.string.dashboard_subscription_usage_with_total, used, total)
                used != null -> stringResource(R.string.dashboard_subscription_usage_used, used)
                else -> null
            }
            val expiryMillis = remote.expireEpochSeconds?.let { it * 1000L }
            val expired = expiryMillis != null && expiryMillis <= System.currentTimeMillis()
            val expiry = when {
                expiryMillis == null -> null
                expired -> stringResource(R.string.dashboard_subscription_expired)
                else -> stringResource(
                    R.string.dashboard_subscription_usage_until,
                    DateFormat.getDateFormat(context).format(Date(expiryMillis)),
                )
            }
            if (traffic != null || expiry != null || subscription.refreshFailed) {
                Column(
                    modifier = Modifier.padding(start = dimens.l, top = dimens.xs),
                    verticalArrangement = Arrangement.spacedBy(dimens.xs),
                ) {
                    if (traffic != null && expiry != null && !expired) {
                        Text(
                            text = traffic + stringResource(R.string.dashboard_subscription_usage_separator) + expiry,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    } else {
                        traffic?.let {
                            Text(text = it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                        if (expired) {
                            Text(text = stringResource(R.string.dashboard_subscription_expired),
                                style = MaterialTheme.typography.bodySmall, color = colors.error)
                        } else if (expiry != null) {
                            Text(text = expiry, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                        }
                    }
                    if (subscription.refreshFailed) {
                        Text(
                            text = stringResource(R.string.dashboard_subscription_refresh_failed),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
