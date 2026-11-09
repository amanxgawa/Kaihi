package app.kaihi.android.feature.importing.impl.ui.mvi

import app.kaihi.android.feature.importing.impl.domain.ImportCounts
import app.kaihi.android.platform.ui.util.UiText

internal object ImportContract {
    data class State(
        val text: String = "",
        val isImporting: Boolean = false,
        val errorMessage: UiText? = null,
    )

    sealed interface Intent {
        data class TextChanged(val text: String) : Intent {
            override fun toString(): String = "TextChanged(text=<redacted>)"
        }

        data object ImportRequested : Intent
        data object CancelRequested : Intent
        data object BackRequested : Intent
    }

    sealed interface Effect {
        data object NavigateBack : Effect
        data class ImportSucceeded(val counts: ImportCounts, val firstAddedServerId: String) : Effect
    }
}
