package app.kaihi.android.platform.ui.util

/** ISO country code of the first flag emoji in a server name, e.g. "🇳🇱 NL-3" → "NL". */
internal fun countryCodeFromFlag(name: String): String? {
    var offset = 0
    while (offset < name.length) {
        val first = Character.codePointAt(name, offset)
        val firstLength = Character.charCount(first)
        val secondOffset = offset + firstLength
        if (first in REGIONAL_INDICATOR_A..REGIONAL_INDICATOR_Z && secondOffset < name.length) {
            val second = Character.codePointAt(name, secondOffset)
            if (second in REGIONAL_INDICATOR_A..REGIONAL_INDICATOR_Z) {
                return "${(first - REGIONAL_INDICATOR_A + 'A'.code).toChar()}" +
                    (second - REGIONAL_INDICATOR_A + 'A'.code).toChar()
            }
        }
        offset += firstLength
    }
    return null
}

/** "🇳🇱 Amsterdam" → "Amsterdam". Only a leading flag is removed. */
internal fun String.withoutLeadingFlag(): String {
    var offset = 0
    while (offset < length) {
        val codePoint = Character.codePointAt(this, offset)
        if (!Character.isWhitespace(codePoint)) break
        offset += Character.charCount(codePoint)
    }

    if (offset >= length) return this
    val first = Character.codePointAt(this, offset)
    if (first !in REGIONAL_INDICATOR_A..REGIONAL_INDICATOR_Z) return this

    val secondOffset = offset + Character.charCount(first)
    if (secondOffset >= length) return this
    val second = Character.codePointAt(this, secondOffset)
    if (second !in REGIONAL_INDICATOR_A..REGIONAL_INDICATOR_Z) return this

    offset = secondOffset + Character.charCount(second)
    while (offset < length) {
        val codePoint = Character.codePointAt(this, offset)
        if (!Character.isWhitespace(codePoint)) break
        offset += Character.charCount(codePoint)
    }
    return if (offset == length) this else substring(offset)
}

internal fun flagFor(countryCode: String?, name: String): String? =
    (countryCode ?: countryCodeFromFlag(name))?.toFlagEmoji()

internal fun String.toFlagEmoji(): String? {
    if (length != 2 || any { it !in 'A'..'Z' }) return null
    return buildString { this@toFlagEmoji.forEach { appendCodePoint(REGIONAL_INDICATOR_A + (it - 'A')) } }
}

private const val REGIONAL_INDICATOR_A = 0x1F1E6
private const val REGIONAL_INDICATOR_Z = 0x1F1FF
