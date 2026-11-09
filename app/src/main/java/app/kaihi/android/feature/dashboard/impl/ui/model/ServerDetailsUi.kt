package app.kaihi.android.feature.dashboard.impl.ui.model

import androidx.compose.runtime.Immutable

@Immutable
internal data class ServerDetailsUi(
    val id: String,
    val name: String,
    val flag: String?,
    val stack: String,
    val address: String,
    val maskingAs: String?,
    val addedAtMillis: Long,
    val isDeployed: Boolean,
)
