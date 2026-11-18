package app.kaihi.android.platform.ui.util

internal fun String.toFlagEmoji(): String? {
    if (length != 2 || any { it !in 'A'..'Z' }) return null
    return buildString { this@toFlagEmoji.forEach { appendCodePoint(REGIONAL_INDICATOR_A + (it - 'A')) } }
}

private const val REGIONAL_INDICATOR_A = 0x1F1E6
