package app.kaihi.android.feature.dashboard.impl.ui.screen.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import app.kaihi.android.feature.dashboard.impl.ui.model.SubscriptionUi
import app.kaihi.android.feature.dashboard.impl.ui.model.SubscriptionKind
import app.kaihi.android.R
import app.kaihi.android.platform.ui.components.SubscriptionUsageCard
import app.kaihi.android.platform.ui.theme.dimens
import app.kaihi.android.platform.ui.theme.kaihiEffectsSpec
import app.kaihi.android.platform.ui.theme.kaihiSpatialSpec

private val revealShape = RoundedCornerShape(bottomStart = SegmentOuterCorner, bottomEnd = SegmentOuterCorner)

@Composable
internal fun SubscriptionGroup(
    subscription: SubscriptionUi,
    onToggleExpanded: () -> Unit,
    onAddServer: (SubscriptionUi) -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onRefresh: () -> Unit,
    onSelectServer: (serverId: String) -> Unit,
    onServerDetails: (serverId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = MaterialTheme.dimens

    Column(modifier = modifier) {
        SubscriptionHeaderRow(
            subscription = subscription,
            onToggleExpanded = onToggleExpanded,
            onShare = onShare,
            onEdit = onEdit,
            onRefresh = onRefresh,
        )

        subscription.usage?.let { usage ->
            val bottomCorner by animateDpAsState(
                targetValue = if (subscription.isExpanded) SegmentInnerCorner else SegmentOuterCorner,
                animationSpec = kaihiSpatialSpec(),
                label = "subscriptionUsageBottomCorner",
            )
            SubscriptionUsageCard(
                usage = usage,
                shape = RoundedCornerShape(
                    topStart = SegmentInnerCorner,
                    topEnd = SegmentInnerCorner,
                    bottomEnd = bottomCorner,
                    bottomStart = bottomCorner,
                ),
                modifier = Modifier.padding(top = dimens.xs),
            )
        }

        AnimatedVisibility(
            visible = subscription.isExpanded,
            modifier = Modifier.clip(revealShape),
            enter = expandVertically(animationSpec = kaihiSpatialSpec(), expandFrom = Alignment.Top) +
                fadeIn(animationSpec = kaihiEffectsSpec()),
            exit = shrinkVertically(animationSpec = kaihiSpatialSpec(), shrinkTowards = Alignment.Top) +
                fadeOut(animationSpec = kaihiEffectsSpec()),
        ) {
            val hasAddRow = subscription.kind != SubscriptionKind.REMOTE
            val segmentCount = subscription.servers.size + 1 + if (hasAddRow) 1 else 0
            Column(
                modifier = Modifier
                    .clip(revealShape)
                    .animateContentSize(animationSpec = kaihiSpatialSpec())
                    .padding(top = dimens.xs),
                verticalArrangement = Arrangement.spacedBy(dimens.xs),
            ) {
                subscription.servers.forEachIndexed { index, server ->
                    key(server.id) {
                        ServerRow(
                            server = server,
                            shape = segmentShape(index = index + 1, count = segmentCount),
                            onClick = { onSelectServer(server.id) },
                            onDetailsClick = { onServerDetails(server.id) },
                        )
                    }
                }

                if (hasAddRow) {
                    AddServerRow(
                        shape = segmentShape(index = segmentCount - 1, count = segmentCount),
                        onClick = { onAddServer(subscription) },
                        labelRes = if (subscription.kind == SubscriptionKind.IMPORTED) {
                            R.string.dashboard_import
                        } else {
                            R.string.dashboard_subscription_add_server
                        },
                    )
                }
            }
        }
    }
}
