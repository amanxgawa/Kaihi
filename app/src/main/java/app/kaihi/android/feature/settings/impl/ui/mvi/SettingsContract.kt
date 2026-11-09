package app.kaihi.android.feature.settings.impl.ui.mvi

import app.kaihi.android.platform.mode.AppMode
import app.kaihi.android.platform.ui.util.UiText

internal object SettingsContract {

    data class State(
        val mode: AppMode = AppMode.NORMAL,
        val ipv6Enabled: Boolean = false,
        val excludedAppsCount: Int = 0,
        val subscriptionUserAgent: String? = null,
        val effectiveSubscriptionUserAgent: String = "",
        val isSubscriptionUserAgentDialogVisible: Boolean = false,
        val subscriptionUserAgentDraft: String = "",
        val subscriptionUserAgentError: UiText? = null,
    )

    sealed interface Intent {
        data class AdvancedModeToggled(val enabled: Boolean) : Intent
        data class Ipv6Toggled(val enabled: Boolean) : Intent
        data object ExcludedAppsRequested : Intent
        data object SubscriptionUserAgentRequested : Intent
        data class SubscriptionUserAgentChanged(val value: String) : Intent
        data object SubscriptionUserAgentSaved : Intent
        data object SubscriptionUserAgentReset : Intent
        data object SubscriptionUserAgentDialogDismissed : Intent
        data object ViewLogsRequested : Intent
        data object BackRequested : Intent
    }

    sealed interface Effect {
        data object NavigateToAdvancedModeIntro : Effect
        data object NavigateToExcludedApps : Effect
        data object NavigateToLogs : Effect
        data object NavigateBack : Effect
    }
}
