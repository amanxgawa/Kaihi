package app.kaihi.android.platform.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.kaihi.android.R

val Sora = FontFamily(
    Font(R.font.sora_regular, FontWeight.Normal),
    Font(R.font.sora_semibold, FontWeight.SemiBold),
    Font(R.font.sora_bold, FontWeight.Bold),
)

val JetBrainsMono = FontFamily(
    Font(R.font.jetbrainsmono_regular, FontWeight.Normal),
    Font(R.font.jetbrainsmono_medium, FontWeight.Medium),
)

private val base = Typography()

val KaihiTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Sora, fontWeight = FontWeight.Bold),
    displayMedium = base.displayMedium.copy(fontFamily = Sora, fontWeight = FontWeight.Bold),
    displaySmall = base.displaySmall.copy(fontFamily = Sora, fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontFamily = Sora, fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Sora, fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Sora, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = Sora, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = Sora, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = Sora, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = Sora),
    bodyMedium = base.bodyMedium.copy(fontFamily = Sora),
    bodySmall = base.bodySmall.copy(fontFamily = Sora),
    labelLarge = base.labelLarge.copy(fontFamily = Sora, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = Sora),
    labelSmall = base.labelSmall.copy(fontFamily = Sora),
)

// For protocol names, hosts, keys, ports
val MonoText = TextStyle(fontFamily = JetBrainsMono, fontSize = 13.sp)
