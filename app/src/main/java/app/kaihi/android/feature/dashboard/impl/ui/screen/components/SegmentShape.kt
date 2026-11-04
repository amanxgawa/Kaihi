package app.kaihi.android.feature.dashboard.impl.ui.screen.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

internal val SegmentOuterCorner = 28.dp
internal val SegmentInnerCorner = 8.dp

internal fun segmentShape(index: Int, count: Int): Shape {
    val top = if (index == 0) SegmentOuterCorner else SegmentInnerCorner
    val bottom = if (index == count - 1) SegmentOuterCorner else SegmentInnerCorner
    return RoundedCornerShape(topStart = top, topEnd = top, bottomEnd = bottom, bottomStart = bottom)
}
