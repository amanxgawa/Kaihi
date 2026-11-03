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

private const val WIDTH_FRACTION = 0.62f

private const val HEIGHT_FRACTION = 0.4f

private val MaxSize = 320.dp

private const val BLEED_X = 0.22f
private const val BLEED_Y = 0.10f

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
