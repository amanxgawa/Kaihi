package app.kaihi.android.platform.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import app.kaihi.android.R

// The launcher icon's K on its own, without the tile behind it.
@Composable
fun KaihiMark(
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Icon(
        painter = painterResource(R.drawable.ic_kaihi_glyph),
        contentDescription = contentDescription,
        tint = tint,
        modifier = modifier,
    )
}
