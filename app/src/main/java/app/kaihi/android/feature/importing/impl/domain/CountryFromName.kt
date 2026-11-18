package app.kaihi.android.feature.importing.impl.domain

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

private const val REGIONAL_INDICATOR_A = 0x1F1E6
private const val REGIONAL_INDICATOR_Z = 0x1F1FF
