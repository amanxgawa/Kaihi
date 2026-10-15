package app.kaihi.android.platform.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import app.kaihi.android.platform.ui.theme.size.AppDimensions
import app.kaihi.android.platform.ui.theme.size.LocalDimensions

private val KaihiColors = darkColorScheme(
    primary = Vermilion,
    onPrimary = Ink,
    primaryContainer = Dusk,
    onPrimaryContainer = Paper,
    inversePrimary = VermilionDeep,
    secondary = Jade,
    onSecondary = Ink,
    secondaryContainer = Line,
    onSecondaryContainer = Paper,
    tertiary = Amber,
    onTertiary = Ink,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = Amber,
    background = Ink,
    onBackground = Paper,
    surface = Ink,
    onSurface = Paper,
    surfaceVariant = Dusk,
    onSurfaceVariant = Mist,
    surfaceTint = Ink,
    inverseSurface = Paper,
    inverseOnSurface = Ink,
    surfaceDim = Ink,
    surfaceBright = Line,
    surfaceContainerLowest = Ink,
    surfaceContainerLow = Night,
    surfaceContainer = Night,
    surfaceContainerHigh = Dusk,
    surfaceContainerHighest = Line,
    outline = Slate,
    outlineVariant = Line,
    error = Rose,
    onError = Ink,
    errorContainer = RoseContainer,
    onErrorContainer = Rose,
)

@Immutable
data class KaihiStatusColors(
    val off: Color = Mist,
    val connecting: Color = Vermilion,
    val connected: Color = Jade,
    val warning: Color = Amber,
    val error: Color = Rose,
    val track: Color = Dusk,
)

private val LocalKaihiStatus = staticCompositionLocalOf { KaihiStatusColors() }

private val dimens = AppDimensions()

object KaihiTheme {
    val status: KaihiStatusColors
        @Composable @ReadOnlyComposable
        get() = LocalKaihiStatus.current
}

// Dark-only, no dynamic color: the palette stays exact on every device.
@Composable
fun KaihiTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalKaihiStatus provides KaihiStatusColors(),
        LocalDimensions provides dimens,
    ) {
        MaterialTheme(
            colorScheme = KaihiColors,
            typography = KaihiTypography,
            content = content,
        )
    }
}
