package app.kaihi.android.feature.hello.impl.ui.screen.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.kaihi.android.R
import app.kaihi.android.platform.ui.icons.KaihiIcons
import app.kaihi.android.platform.ui.theme.size.dimens
import app.kaihi.android.platform.ui.util.UiText

@Composable
internal fun HelloContent(
    onSetUpServerClick: () -> Unit,
    onConnectOrImportClick: () -> Unit,
    onLearnFirstClick: () -> Unit,
    onViewSourceCodeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val dimens = MaterialTheme.dimens

    // The watermark sits outside the inset padding and the scroll container so it can reach the screen
    // edges (and run under the status bar) instead of being clipped at the scroll viewport.
    Box(modifier = modifier.fillMaxSize()) {
        HelloWatermark(
            scrollOffset = { scrollState.value },
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(scrollState)
                .padding(horizontal = dimens.l, vertical = dimens.l),
            // Centers the block when it's shorter than the viewport (the common case); once content
            // overflows, verticalScroll naturally takes over and this has no effect.
            verticalArrangement = Arrangement.spacedBy(dimens.m, Alignment.CenterVertically)
        ) {
            HelloHeader(subtitle = UiText.Resource(R.string.hello_header_subtitle))

            HelloActionCard(
                title = UiText.Resource(R.string.hello_setup_title),
                description = UiText.Resource(R.string.hello_setup_description),
                icon = KaihiIcons.Outlined.Dns,
                onClick = onSetUpServerClick,
            )

            HelloActionCard(
                title = UiText.Resource(R.string.hello_connect_title),
                description = UiText.Resource(R.string.hello_connect_description),
                icon = KaihiIcons.Outlined.Cable,
                onClick = onConnectOrImportClick,
                enabled = false,
                badgeText = UiText.Resource(R.string.badge_soon),
            )

            HelloActionCard(
                title = UiText.Resource(R.string.hello_learn_title),
                description = UiText.Resource(R.string.hello_learn_description),
                icon = KaihiIcons.Outlined.AutoStories,
                onClick = onLearnFirstClick,
                enabled = false,
                badgeText = UiText.Resource(R.string.badge_soon),
            )

            HelloBottomActions(
                onViewSourceCodeClick = onViewSourceCodeClick,
            )
        }
    }
}
