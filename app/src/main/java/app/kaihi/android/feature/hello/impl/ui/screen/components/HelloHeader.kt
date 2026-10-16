package app.kaihi.android.feature.hello.impl.ui.screen.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import app.kaihi.android.R
import app.kaihi.android.platform.ui.theme.size.dimens
import app.kaihi.android.platform.ui.util.UiText
import app.kaihi.android.platform.ui.util.asString

@Composable
internal fun HelloHeader(subtitle: UiText) {
    val highlight = stringResource(id = R.string.hello_header_title_highlight)
    val fullTitle = stringResource(id = R.string.hello_header_title, highlight)
    val highlightStart = fullTitle.lastIndexOf(highlight).coerceAtLeast(0)

    val title = buildAnnotatedString {
        append(fullTitle.substring(0, highlightStart))
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
            append(fullTitle.substring(highlightStart))
        }
    }

    Column {
        Text(
            text = title,
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
