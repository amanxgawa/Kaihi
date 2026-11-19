package app.kaihi.android.feature.importing.impl.ui.mvi

import app.kaihi.android.feature.importing.impl.ui.model.AlreadyAddedUi
import app.kaihi.android.feature.importing.impl.ui.model.ImportPreviewUi
import app.kaihi.android.platform.ui.util.UiText

internal object ImportContract {
    data class State(
        val text: String = "",
        val isImporting: Boolean = false,
        val errorMessage: UiText? = null,
        val preview: ImportPreviewUi? = null,
        val alreadyAdded: AlreadyAddedUi? = null,
        val expandedGroups: Set<String> = emptySet(),
        val isAdding: Boolean = false,
    )

    sealed interface Intent {
        data class TextChanged(val text: String) : Intent {
            override fun toString(): String = "TextChanged(text=<redacted>)"
        }

        data class Prefilled(val text: String) : Intent {
            override fun toString(): String = "Prefilled(text=<redacted>)"
        }

        data object ImportRequested : Intent
        data object AddRequested : Intent
        data object PreviewDismissed : Intent
        data class GroupToggled(val key: String) : Intent
        data object ShowExistingRequested : Intent
        data object CancelRequested : Intent
        data object BackRequested : Intent
    }

    sealed interface Effect {
        data object NavigateBack : Effect
        data class ImportSucceeded(val firstAddedServerId: String) : Effect
        data class ShowExisting(val serverId: String?) : Effect
    }
}
