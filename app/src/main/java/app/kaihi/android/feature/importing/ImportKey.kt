package app.kaihi.android.feature.importing

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable
import java.util.UUID

/** The redacted toString is Navigation3's default content key. */
@Serializable
data class ImportKey(
    val text: String? = null,
    val requestId: String = UUID.randomUUID().toString(),
) : NavKey {
    override fun toString(): String =
        "ImportKey(text=${if (text == null) "null" else "<redacted>"}, requestId=$requestId)"
}
