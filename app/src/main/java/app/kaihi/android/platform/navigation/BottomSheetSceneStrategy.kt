package app.kaihi.android.platform.navigation

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.rememberLifecycleOwner
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavMetadataKey
import androidx.navigation3.runtime.contains
import androidx.navigation3.runtime.metadata
import androidx.navigation3.scene.OverlayScene
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SceneStrategyScope

@Stable
class BottomSheetChrome internal constructor() {
    /** When true the sheet has no drag handle and ignores drag gestures. */
    var isPinned: Boolean by mutableStateOf(false)
}

val LocalBottomSheetChrome = staticCompositionLocalOf<BottomSheetChrome?> { null }

class BottomSheetSceneStrategy<T : Any> : SceneStrategy<T> {

    override fun SceneStrategyScope<T>.calculateScene(entries: List<NavEntry<T>>): Scene<T>? {
        val last = entries.lastOrNull() ?: return null
        if (BottomSheetKey !in last.metadata) return null
        return BottomSheetScene(
            key = last.contentKey,
            entry = last,
            previousEntries = entries.dropLast(1),
            overlaidEntries = entries.dropLast(1),
            onBack = onBack,
        )
    }

    companion object {
        object BottomSheetKey : NavMetadataKey<Boolean>

        fun bottomSheet(): Map<String, Any> = metadata { put(BottomSheetKey, true) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
internal class BottomSheetScene<T : Any>(
    override val key: Any,
    private val entry: NavEntry<T>,
    override val previousEntries: List<NavEntry<T>>,
    override val overlaidEntries: List<NavEntry<T>>,
    private val onBack: () -> Unit,
) : OverlayScene<T> {

    private var sheetState: SheetState? = null

    override val entries: List<NavEntry<T>> = listOf(entry)

    override val content: @Composable (() -> Unit) = {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val chrome = remember { BottomSheetChrome() }
        SideEffect { sheetState = state }
        ModalBottomSheet(
            onDismissRequest = onBack,
            sheetState = state,
            sheetGesturesEnabled = !chrome.isPinned,
            dragHandle = if (chrome.isPinned) null else { { BottomSheetDefaults.DragHandle() } },
        ) {
            CompositionLocalProvider(
                LocalLifecycleOwner provides rememberLifecycleOwner(),
                LocalBottomSheetChrome provides chrome,
            ) {
                entry.Content()
            }
        }
    }

    override suspend fun onRemove() {
        sheetState?.hide()
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as BottomSheetScene<*>

        return key == other.key &&
            previousEntries == other.previousEntries &&
            overlaidEntries == other.overlaidEntries &&
            entry == other.entry
    }

    override fun hashCode(): Int {
        return key.hashCode() * 31 +
            previousEntries.hashCode() * 31 +
            overlaidEntries.hashCode() * 31 +
            entry.hashCode() * 31
    }

    override fun toString(): String {
        return "BottomSheetScene(key=$key, entry=$entry, previousEntries=$previousEntries, overlaidEntries=$overlaidEntries)"
    }
}
