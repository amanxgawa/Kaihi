package app.kaihi.android.feature.dashboard.impl.ui.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

@Immutable
internal data class SubscriptionUi(
    val id: String,
    val name: String,
    val kind: SubscriptionKind,
    val isExpanded: Boolean,
    val containsSelectedServer: Boolean,
    val servers: ImmutableList<ServerUi>,
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
)
