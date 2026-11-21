package app.kaihi.android.feature.dashboard.impl.domain

import app.kaihi.android.feature.dashboard.impl.domain.model.ServerListPreferences
import kotlinx.coroutines.flow.StateFlow

internal interface ServerListPreferencesRepository {
    val preferences: StateFlow<ServerListPreferences>
    fun select(serverId: String)
    fun setCollapsed(subscriptionId: String, collapsed: Boolean)
}
