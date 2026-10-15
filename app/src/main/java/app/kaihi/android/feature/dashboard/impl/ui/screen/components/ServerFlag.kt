package app.kaihi.android.feature.dashboard.impl.ui.screen.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import app.kaihi.android.platform.ui.icons.KaihiIcons
import app.kaihi.android.platform.ui.theme.size.dimens

@Composable
internal fun ServerFlag(
    flag: String?,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
) {
    val dimens = MaterialTheme.dimens

    Box(
        modifier = modifier
            .size(dimens.control)
            .clip(CircleShape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        if (flag != null) {
            Text(text = flag, style = MaterialTheme.typography.titleMedium)
        } else {
            Icon(
                imageVector = KaihiIcons.Outlined.Dns,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(dimens.icon),
            )
        }
    }
}
