package app.kaihi.android.feature.hello.impl.ui.screen.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import app.kaihi.android.R
import app.kaihi.android.platform.ui.components.KaihiMark
import app.kaihi.android.platform.ui.theme.size.dimens
import app.kaihi.android.platform.ui.util.UiText
import app.kaihi.android.platform.ui.util.asString

private const val MARK_ID = "kaihi_mark"

@Composable
internal fun HelloHeader(subtitle: UiText) {
    val highlight = stringResource(id = R.string.hello_header_title_highlight)
    val fullTitle = stringResource(id = R.string.hello_header_title, highlight)
    val highlightStart = fullTitle.lastIndexOf(highlight).coerceAtLeast(0)
    val appName = stringResource(id = R.string.app_name)

    // The mark is a character of the title rather than a sibling above it, so it sits on the first line
    // at the far left and the rest of the title wraps the same way it would without it.
    val title = buildAnnotatedString {
        appendInlineContent(MARK_ID, alternateText = appName)
        append(" ")
        append(fullTitle.substring(0, highlightStart))
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
            append(fullTitle.substring(highlightStart))
        }
    }
    val inlineContent = mapOf(
        MARK_ID to InlineTextContent(
            Placeholder(
                width = 1.2.em,
                height = 1.2.em,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
            ),
        ) { KaihiMark(modifier = Modifier.fillMaxSize()) },
    )

    Column {
        Text(
            text = title,
            inlineContent = inlineContent,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(modifier = Modifier.height(MaterialTheme.dimens.s))

        Text(
            text = subtitle.asString(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
