package app.kaihi.android.feature.importing.impl.domain

/** Text written by a subscription panel. Removes characters that make text display differently from what it contains. */
internal fun cleanUntrustedText(value: String, maxCodePoints: Int, keepNewlines: Boolean = false): String {
    val cleaned = buildString {
        var offset = 0
        while (offset < value.length) {
            val codePoint = Character.codePointAt(value, offset)
            offset += Character.charCount(codePoint)
            when {
                codePoint.isBidiControl() -> Unit
                Character.isISOControl(codePoint) && !(keepNewlines && codePoint == '\n'.code) -> append(' ')
                else -> appendCodePoint(codePoint)
            }
        }
    }.trim()

    val limit = maxCodePoints.coerceAtLeast(0)
    var offset = 0
    var count = 0
    while (offset < cleaned.length && count < limit) {
        offset += Character.charCount(Character.codePointAt(cleaned, offset))
        count++
    }
    if (offset == cleaned.length) return cleaned
    return cleaned.substring(0, offset) + "…"
}

private fun Int.isBidiControl(): Boolean =
    this == 0x061C || this == 0x200E || this == 0x200F || this in 0x202A..0x202E || this in 0x2066..0x2069
