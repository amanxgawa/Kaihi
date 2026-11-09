package app.kaihi.android.feature.settings.impl.ui.mvi

import app.kaihi.android.feature.settings.impl.domain.InstalledApp
import app.kaihi.android.platform.ui.util.UiText

internal object ExcludedAppsContract {

    data class State(
        val apps: List<InstalledApp> = emptyList(),
        val excludedPackages: Set<String> = emptySet(),
        val query: String = "",
        val isLoading: Boolean = true,
        val errorMessage: UiText? = null,
    )

    sealed interface Intent {
        data class QueryChanged(val query: String) : Intent
        data class AppToggled(val packageName: String, val excluded: Boolean) : Intent
        data object BackRequested : Intent
    }

    sealed interface Effect {
        data object NavigateBack : Effect
    }
}
