package app.kaihi.android.feature.settings.impl.ui.mvi

import app.kaihi.android.platform.mode.AppMode

internal object SettingsContract {

    data class State(
        val mode: AppMode = AppMode.NORMAL,
        val ipv6Enabled: Boolean = false,
        val excludedAppsCount: Int = 0,
    )

    sealed interface Intent {
        data class AdvancedModeToggled(val enabled: Boolean) : Intent
        data class Ipv6Toggled(val enabled: Boolean) : Intent
        data object ExcludedAppsRequested : Intent
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
