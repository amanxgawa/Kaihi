package app.kaihi.android.feature.hello.impl.ui.screen

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import app.kaihi.android.feature.hello.impl.ui.screen.components.HelloContent
import app.kaihi.android.platform.ui.components.AddServerSheet

private const val SOURCE_CODE_URL = "https://github.com/amanxgawa/Kaihi"

@Composable
internal fun HelloScreen(
    onSetUpServerClick: () -> Unit,
    onPaste: (String) -> Unit,
    onEnterManually: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var isAddServerSheetVisible by rememberSaveable { mutableStateOf(false) }

    HelloContent(
        modifier = modifier,
        onSetUpServerClick = onSetUpServerClick,
        onAddServerClick = { isAddServerSheetVisible = true },
        onViewSourceCodeClick = {
            context.startActivity(Intent(Intent.ACTION_VIEW, SOURCE_CODE_URL.toUri()))
        },
    )

    if (isAddServerSheetVisible) {
        AddServerSheet(
            onDismiss = { isAddServerSheetVisible = false },
            onPaste = { text ->
                isAddServerSheetVisible = false
                onPaste(text)
            },
            onEnterManually = {
                isAddServerSheetVisible = false
                onEnterManually()
            },
            onSetUpServer = null,
        )
    }
}
