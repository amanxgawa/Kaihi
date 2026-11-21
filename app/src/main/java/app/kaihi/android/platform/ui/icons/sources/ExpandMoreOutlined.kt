package app.kaihi.android.platform.ui.icons.sources

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

@Suppress("CheckReturnValue")
internal val _expand_more_outlined: ImageVector
    get() {
        if (__expand_more_outlined != null) {
            return __expand_more_outlined!!
        }
        __expand_more_outlined =
            ImageVector.Builder(
                    name = "expand_more",
                    defaultWidth = 24.dp,
                    defaultHeight = 24.dp,
                    viewportWidth = 24f,
                    viewportHeight = 24f,
                )
                .apply {
                    path(
                        fill = SolidColor(Color.Black),
                        fillAlpha = 1f,
                        stroke = null,
                        strokeAlpha = 1f,
                        strokeLineWidth = 1f,
                        strokeLineCap = StrokeCap.Butt,
                        strokeLineJoin = StrokeJoin.Bevel,
                        strokeLineMiter = 1f,
                        pathFillType = PathFillType.NonZero,
                    ) {
                        moveTo(12f, 15.38f)
                        lineToRelative(-6f, -6f)
                        lineTo(7.4f, 7.97f)
                        lineToRelative(4.6f, 4.6f)
                        lineToRelative(4.6f, -4.6f)
                        lineTo(18f, 9.38f)
                        lineToRelative(-6f, 6f)
                        close()
                    }
                }
                .build()
        return __expand_more_outlined!!
    }

private var __expand_more_outlined: ImageVector? = null
