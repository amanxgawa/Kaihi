package app.kaihi.android.feature.hello.impl.ui.screen.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.kaihi.android.platform.ui.components.KaihiMark

// The K is ~64% of its drawable's width, so this makes it span ~40% of the screen width.
private const val WIDTH_FRACTION = 0.62f

// On a short screen (landscape, split-screen) the width fraction alone would swallow the whole height.
private const val HEIGHT_FRACTION = 0.4f

// Tablets and foldables: past this the K stops growing and just sits in the corner.
private val MaxSize = 320.dp

// How far the box overhangs the top-right corner, as a share of its size. Tuned so only the arm tips
// and the top of the stem are cropped, which keeps the letter readable.
private const val BLEED_X = 0.22f
private const val BLEED_Y = 0.10f

// The K drawn huge and tone-on-tone in the top-right corner, behind the header. It is decoration, so it
// is built to never fight the content on top of it:
// - Dusk on Ink is subtle, but the header text still clears 4.5:1 on top of it (Paper ~13:1, Mist
//   ~5.6:1, the vermilion highlight ~5.1:1), so there is no spot where overlap hurts legibility.
// - Sized from the available space and capped, so it shrinks on short screens and stops on large ones.
// - Pinned to the top of the page and scrolled by the caller's offset, so on a small screen it moves away
//   with the header instead of staying put behind the cards.
// - Absent from layout and semantics: it takes no space, never intercepts touches, and TalkBack skips it.
@Composable
internal fun HelloWatermark(
    scrollOffset: () -> Int,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val size = minOf(maxWidth * WIDTH_FRACTION, maxHeight * HEIGHT_FRACTION, MaxSize)

        KaihiMark(
            tint = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = size * BLEED_X, y = -size * BLEED_Y)
                .size(size)
                // Read in the layer so scrolling re-draws this instead of recomposing it.
                .graphicsLayer { translationY = -scrollOffset().toFloat() },
        )
    }
}
