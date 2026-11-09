package app.kaihi.android.feature.dashboard

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data class ServerKey(val serverId: String) : NavKey
