package app.kaihi.android.feature.dashboard.impl.ui.mvi

import app.kaihi.android.R
import app.kaihi.android.core.vpn.SubscriptionFetchFailure
import app.kaihi.android.core.vpn.XraySubscription
import app.kaihi.android.core.vpn.XraySubscriptionSource
import app.kaihi.android.core.vpn.findServer
import app.kaihi.android.core.vpn.findSubscription
import app.kaihi.android.core.vpn.toVlessUri
import app.kaihi.android.core.vpn.toVlessUriList
import app.kaihi.android.feature.dashboard.impl.domain.RemoteSubscriptionRefresher
import app.kaihi.android.feature.dashboard.impl.domain.RemoteSubscriptionRefreshOutcome
import app.kaihi.android.feature.dashboard.impl.domain.ServerLibraryCoordinator
import app.kaihi.android.feature.dashboard.impl.domain.isStale
import app.kaihi.android.feature.dashboard.impl.domain.model.ServerLibrary
import app.kaihi.android.platform.ui.mvi.ViewModelDelegate
import app.kaihi.android.platform.ui.util.UiText
import javax.inject.Inject
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

internal class ServerLibraryDelegate @Inject constructor(
    private val coordinator: ServerLibraryCoordinator,
    private val subscriptionRefresher: RemoteSubscriptionRefresher,
) : ViewModelDelegate<DashboardContract.State, DashboardContract.Effect>() {

    private var library: ServerLibrary? = null
    private val refreshingIds = mutableSetOf<String>()
    private val failedRefreshIds = mutableSetOf<String>()
    private var automaticRefreshStarted = false
    private var pendingRemoteShareId: String? = null

    val selectedServerId: String?
        get() = library?.selectedServer?.id

    fun observe() {
        coordinator.library
            .onEach { latest ->
                library = latest
                reduce {
                    copy(
                        isLibraryLoaded = true,
                        subscriptions = latest.toSubscriptionsUi(refreshingIds, failedRefreshIds),
                        serverDetails = serverDetails?.let { latest.subscriptions.findServer(it.id)?.toDetailsUi() },
                        subscriptionEditor = subscriptionEditor?.let { editor ->
                            latest.subscriptions.findSubscription(editor.id)?.toEditorUi()
                        },
                        remoteShareConfirmationSubscriptionId = pendingRemoteShareId?.takeIf { id ->
                            latest.subscriptions.findSubscription(id)?.source is XraySubscriptionSource.Remote
                        },
                    )
                }
                if (!automaticRefreshStarted) {
                    automaticRefreshStarted = true
                    scope.launch { refreshStaleSubscriptions(latest.subscriptions) }
                }
            }
            .launchIn(scope)
    }

    /** @return whether the selection moved, and with it whether a running session has to follow. */
    fun select(serverId: String): Boolean {
        if (serverId == selectedServerId) return false
        coordinator.select(serverId)
        return true
    }

    fun toggleExpanded(subscriptionId: String) {
        val collapsed = library?.collapsedSubscriptionIds ?: return
        coordinator.setCollapsed(subscriptionId, collapsed = subscriptionId !in collapsed)
    }

    fun serverIdsOf(subscriptionId: String): Set<String> =
        library?.subscriptions?.findSubscription(subscriptionId)?.servers?.mapTo(mutableSetOf()) { it.id }.orEmpty()

    fun shareSubscription(subscriptionId: String) {
        library?.subscriptions?.findSubscription(subscriptionId)?.let { subscription ->
            if (subscription.source is XraySubscriptionSource.Remote) {
                pendingRemoteShareId = subscriptionId
                reduce { copy(remoteShareConfirmationSubscriptionId = subscriptionId) }
            } else {
                sendEffect(DashboardContract.Effect.ShareText(subscription.toVlessUriList()))
            }
        }
    }

    fun confirmRemoteSubscriptionShare() {
        val subscriptionId = pendingRemoteShareId ?: return
        pendingRemoteShareId = null
        reduce { copy(remoteShareConfirmationSubscriptionId = null) }
        val source = library?.subscriptions?.findSubscription(subscriptionId)?.source as? XraySubscriptionSource.Remote
            ?: return
        sendEffect(DashboardContract.Effect.ShareText(source.url))
    }

    fun dismissRemoteSubscriptionShare() {
        pendingRemoteShareId = null
        reduce { copy(remoteShareConfirmationSubscriptionId = null) }
    }

    fun refreshSubscription(subscriptionId: String) {
        scope.launch { refresh(subscriptionId, manual = true) }
    }

    fun shareServer(serverId: String) {
        library?.subscriptions?.findServer(serverId)?.let { server ->
            sendEffect(DashboardContract.Effect.ShareText(server.toVlessUri()))
        }
    }

    fun openServerDetails(serverId: String) {
        val details = library?.subscriptions?.findServer(serverId)?.toDetailsUi() ?: return
        reduce { copy(serverDetails = details) }
    }

    fun dismissServerDetails() {
        reduce { copy(serverDetails = null) }
    }

    suspend fun forgetServer(serverId: String) {
        reduce { copy(serverDetails = null) }
        coordinator.forgetServer(serverId)
    }

    fun openSubscriptionEditor(subscriptionId: String) {
        val subscription = library?.subscriptions?.findSubscription(subscriptionId) ?: return
        reduce { copy(subscriptionEditor = subscription.toEditorUi()) }
    }

    fun dismissSubscriptionEditor() {
        reduce { copy(subscriptionEditor = null) }
    }

    suspend fun renameSubscription(subscriptionId: String, name: String) {
        reduce { copy(subscriptionEditor = null) }
        coordinator.renameSubscription(subscriptionId, name)
    }

    suspend fun deleteSubscription(subscriptionId: String) {
        reduce { copy(subscriptionEditor = null) }
        coordinator.deleteSubscription(subscriptionId)
    }

    private suspend fun refreshStaleSubscriptions(subscriptions: List<XraySubscription>) {
        val nowMillis = System.currentTimeMillis()
        subscriptions.forEach { subscription ->
            val remote = subscription.source as? XraySubscriptionSource.Remote ?: return@forEach
            if (remote.isStale(nowMillis)) refresh(subscription.id, manual = false)
        }
    }

    private suspend fun refresh(subscriptionId: String, manual: Boolean) {
        if (!refreshingIds.add(subscriptionId)) return
        failedRefreshIds.remove(subscriptionId)
        updateSubscriptionUi()
        try {
            when (val outcome = subscriptionRefresher.refresh(subscriptionId)) {
                is RemoteSubscriptionRefreshOutcome.Success -> failedRefreshIds.remove(subscriptionId)
                is RemoteSubscriptionRefreshOutcome.FetchFailed,
                RemoteSubscriptionRefreshOutcome.NothingImportable -> {
                    failedRefreshIds.add(subscriptionId)
                    if (manual) outcome.toUiText()?.let { message -> reduce { copy(errorMessage = message) } }
                }
                RemoteSubscriptionRefreshOutcome.AlreadyRefreshing,
                RemoteSubscriptionRefreshOutcome.SubscriptionUnavailable -> Unit
            }
        } finally {
            refreshingIds.remove(subscriptionId)
            updateSubscriptionUi()
        }
    }

    private fun updateSubscriptionUi() {
        val latest = library ?: return
        reduce { copy(subscriptions = latest.toSubscriptionsUi(refreshingIds, failedRefreshIds)) }
    }

    private fun RemoteSubscriptionRefreshOutcome.toUiText(): UiText? = when (this) {
        is RemoteSubscriptionRefreshOutcome.FetchFailed -> UiText.Resource(
            when (reason) {
                SubscriptionFetchFailure.INSECURE_URL -> R.string.import_fetch_error_insecure_url
                SubscriptionFetchFailure.NETWORK -> R.string.import_fetch_error_network
                SubscriptionFetchFailure.TIMEOUT -> R.string.import_fetch_error_timeout
                SubscriptionFetchFailure.HTTP_STATUS -> R.string.import_fetch_error_http_status
                SubscriptionFetchFailure.TOO_LARGE -> R.string.import_fetch_error_too_large
                SubscriptionFetchFailure.TOO_MANY_REDIRECTS -> R.string.import_fetch_error_redirects
                SubscriptionFetchFailure.UNSUPPORTED_FORMAT -> R.string.import_fetch_error_unsupported_format
                SubscriptionFetchFailure.EMPTY -> R.string.import_fetch_error_empty
            },
        )
        RemoteSubscriptionRefreshOutcome.NothingImportable -> UiText.Resource(R.string.dashboard_subscription_refresh_empty)
        RemoteSubscriptionRefreshOutcome.AlreadyRefreshing,
        RemoteSubscriptionRefreshOutcome.SubscriptionUnavailable,
        is RemoteSubscriptionRefreshOutcome.Success -> null
    }
}
