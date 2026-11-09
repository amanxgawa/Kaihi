package app.kaihi.android.core.vpn

import java.io.ByteArrayOutputStream
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.Base64

internal fun decodeUriComponent(value: String): String? {
    if (!value.isWellFormedUnicode()) return null
    val bytes = ByteArrayOutputStream(value.length)
    var index = 0
    while (index < value.length) {
        val char = value[index]
        if (char == '%') {
            if (index + 2 >= value.length) return null
            val high = value[index + 1].digitToIntOrNull(16) ?: return null
            val low = value[index + 2].digitToIntOrNull(16) ?: return null
            bytes.write((high shl 4) or low)
            index += 3
        } else {
            val codePoint = Character.codePointAt(value, index)
            val text = String(Character.toChars(codePoint))
            bytes.write(text.toByteArray(StandardCharsets.UTF_8))
            index += Character.charCount(codePoint)
        }
    }
    return decodeUtf8(bytes.toByteArray())
}

internal fun cleanDisplayText(value: String, maxCodePoints: Int): String {
    val filtered = buildString {
        var index = 0
        while (index < value.length) {
            val codePoint = Character.codePointAt(value, index)
            val category = Character.getType(codePoint)
            if (category != Character.CONTROL.toInt() && category != Character.FORMAT.toInt()) {
                appendCodePoint(codePoint)
            }
            index += Character.charCount(codePoint)
        }
    }.trimUnicodeWhitespace()
    return filtered.takeCodePoints(maxCodePoints)
}

internal fun decodeBase64Utf8(value: String): String? {
    val compact = buildString(value.length) {
        value.forEach { char ->
            if (!char.isWhitespace() && !Character.isSpaceChar(char)) append(char)
        }
    }
    if (compact.isEmpty() || compact.any { it !in BASE64_ALPHABET }) return null

    val firstPadding = compact.indexOf('=')
    val unpadded = if (firstPadding < 0) compact else compact.substring(0, firstPadding)
    val paddingCount = compact.length - unpadded.length
    if (paddingCount > 2 || (firstPadding >= 0 && compact.substring(firstPadding).any { it != '=' })) return null
    val remainder = unpadded.length % 4
    if (remainder == 1) return null
    val requiredPadding = (4 - remainder) % 4
    if (paddingCount != 0 && (paddingCount != requiredPadding || compact.length % 4 != 0)) return null

    val normalized = unpadded.replace('-', '+').replace('_', '/')
    val padded = normalized + "=".repeat(requiredPadding)
    val decoded = try {
        Base64.getDecoder().decode(padded)
    } catch (_: IllegalArgumentException) {
        return null
    }
    val canonical = Base64.getEncoder().withoutPadding().encodeToString(decoded)
        .replace('+', '-').replace('/', '_')
    if (canonical != unpadded.replace('+', '-').replace('/', '_')) return null
    return decodeUtf8(decoded)
}

internal fun String.takeCodePoints(maxCodePoints: Int): String {
    if (maxCodePoints <= 0) return ""
    val end = offsetByCodePoints(0, minOf(codePointCount(0, length), maxCodePoints))
    return substring(0, end)
}

internal fun isAbsoluteWebUrl(value: String, scheme: String, forbidUserInfo: Boolean = false): Boolean {
    if (value.isEmpty() || value.any { it.isWhitespace() || Character.isSpaceChar(it) }) return false
    val uri = try {
        URI(value)
    } catch (_: Exception) {
        return false
    }
    return uri.isAbsolute && uri.scheme.equals(scheme, ignoreCase = true) &&
        !uri.rawAuthority.isNullOrEmpty() && !uri.host.isNullOrEmpty() &&
        (!forbidUserInfo || uri.rawUserInfo == null) &&
        (uri.port == -1 || uri.port in 1..65535)
}

internal fun looksLikeLinkList(value: String): Boolean = value
    .lineSequence()
    .map(String::trim)
    .any { line -> !line.startsWith('#') && LINK_SCHEME_REGEX.containsMatchIn(line) }

private fun decodeUtf8(value: ByteArray): String? = try {
    StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(value))
        .toString()
} catch (_: Exception) {
    null
}

private fun String.isWellFormedUnicode(): Boolean {
    var index = 0
    while (index < length) {
        val char = this[index]
        when {
            Character.isHighSurrogate(char) -> {
                if (index + 1 >= length || !Character.isLowSurrogate(this[index + 1])) return false
                index += 2
            }
            Character.isLowSurrogate(char) -> return false
            else -> index++
        }
    }
    return true
}

private fun String.trimUnicodeWhitespace(): String {
    var start = 0
    var end = length
    while (start < end) {
        val codePoint = Character.codePointAt(this, start)
        if (!codePoint.isUnicodeWhitespace()) break
        start += Character.charCount(codePoint)
    }
    while (end > start) {
        val codePoint = Character.codePointBefore(this, end)
        if (!codePoint.isUnicodeWhitespace()) break
        end -= Character.charCount(codePoint)
    }
    return substring(start, end)
}

private fun Int.isUnicodeWhitespace(): Boolean = Character.isWhitespace(this) || Character.isSpaceChar(this)

private val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=_-".toSet()
private val LINK_SCHEME_REGEX = Regex("^[A-Za-z][A-Za-z0-9+.-]*://")
