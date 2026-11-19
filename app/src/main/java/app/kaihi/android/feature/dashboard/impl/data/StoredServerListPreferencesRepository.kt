package app.kaihi.android.feature.dashboard.impl.data

import app.kaihi.android.core.storage.AppStorage
import app.kaihi.android.core.storage.StorageDomain
import app.kaihi.android.feature.dashboard.impl.domain.ServerListPreferencesRepository
import app.kaihi.android.feature.dashboard.impl.domain.model.ServerListPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

/**
 * Presentation state, not subscription data, so it stays out of `core` and out of encrypted storage:
 * the ids are random UUIDs and say nothing about the servers behind them.
 */
internal class StoredServerListPreferencesRepository @Inject constructor(
    private val appStorage: AppStorage,
) : ServerListPreferencesRepository {
    private val _preferences = MutableStateFlow(read())
    override val preferences: StateFlow<ServerListPreferences> = _preferences.asStateFlow()

    override fun select(serverId: String) = update { copy(selectedServerId = serverId) }

    override fun setCollapsed(subscriptionId: String, collapsed: Boolean) = update {
        copy(
            collapsedSubscriptionIds = if (collapsed) {
                collapsedSubscriptionIds + subscriptionId
            } else {
                collapsedSubscriptionIds - subscriptionId
            },
        )
    }

    private fun update(transform: ServerListPreferences.() -> ServerListPreferences) {
        val next = _preferences.updateAndGet(transform)
        next.selectedServerId
            ?.let { appStorage.putString(StorageDomain.EXPERIENCE, KEY_SELECTED_SERVER_ID, it) }
            ?: appStorage.remove(StorageDomain.EXPERIENCE, KEY_SELECTED_SERVER_ID)
        appStorage.putString(
            StorageDomain.EXPERIENCE,
            KEY_COLLAPSED_SUBSCRIPTION_IDS,
            next.collapsedSubscriptionIds.joinToString(SEPARATOR),
        )
    }

    private fun read() = ServerListPreferences(
        selectedServerId = appStorage.getString(StorageDomain.EXPERIENCE, KEY_SELECTED_SERVER_ID),
        collapsedSubscriptionIds = appStorage.getString(StorageDomain.EXPERIENCE, KEY_COLLAPSED_SUBSCRIPTION_IDS)
            ?.split(SEPARATOR)
            ?.filterTo(mutableSetOf(), String::isNotBlank)
            .orEmpty(),
    )

    private companion object {
        const val SEPARATOR = ","
        const val KEY_SELECTED_SERVER_ID = "selected_server_id"
        const val KEY_COLLAPSED_SUBSCRIPTION_IDS = "collapsed_subscription_ids"
    }
}
