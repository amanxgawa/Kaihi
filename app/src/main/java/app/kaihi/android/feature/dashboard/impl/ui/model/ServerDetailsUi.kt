package app.kaihi.android.feature.dashboard.impl.ui.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

@Immutable
internal data class ServerDetailsUi(
    val id: String,
    val name: String,
    val flag: String?,
    val stack: String,
    val addresses: ImmutableList<String>,
    val maskingAs: String?,
    val addedAtMillis: Long,
    val isDeployed: Boolean,
)
