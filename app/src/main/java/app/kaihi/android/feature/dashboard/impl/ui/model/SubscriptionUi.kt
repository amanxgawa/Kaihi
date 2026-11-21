package app.kaihi.android.feature.dashboard.impl.ui.model

import androidx.compose.runtime.Immutable
import app.kaihi.android.platform.ui.components.SubscriptionUsageUi
import kotlinx.collections.immutable.ImmutableList

@Immutable
internal data class SubscriptionUi(
    val id: String,
    val name: String,
    val kind: SubscriptionKind,
    val isExpanded: Boolean,
    val containsSelectedServer: Boolean,
    val servers: ImmutableList<ServerUi>,
    val usage: SubscriptionUsageUi? = null,
    val isRefreshing: Boolean = false,
)

internal enum class SubscriptionKind {
    DEPLOYED,
    IMPORTED,
    REMOTE,
}

@Immutable
internal data class ServerUi(
    val id: String,
    val name: String,
    val stack: String,
    val flag: String?,
    val isSelected: Boolean,
)

@Immutable
internal data class SubscriptionEditorUi(
    val id: String,
    val name: String,
    val usedBytes: Long? = null,
    val totalBytes: Long? = null,
    val expireEpochSeconds: Long? = null,
    val updateIntervalHours: Int? = null,
    val lastUpdatedMillis: Long? = null,
    val announce: String? = null,
    val supportUrl: String? = null,
    val webPageUrl: String? = null,
    val isRemote: Boolean = false,
)
