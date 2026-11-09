package app.kaihi.android.core.vpn

import java.util.Locale

data class SubscriptionMetadata(
    val title: String? = null,
    val userInfo: XraySubscriptionUserInfo? = null,
    val updateIntervalHours: Int? = null,
    val supportUrl: String? = null,
    val webPageUrl: String? = null,
    val announce: String? = null,
) {
    fun overriddenBy(other: SubscriptionMetadata): SubscriptionMetadata = SubscriptionMetadata(
        title = other.title ?: title,
        userInfo = other.userInfo ?: userInfo,
        updateIntervalHours = other.updateIntervalHours ?: updateIntervalHours,
        supportUrl = other.supportUrl ?: supportUrl,
        webPageUrl = other.webPageUrl ?: webPageUrl,
        announce = other.announce ?: announce,
    )
}

data class ParsedSubscriptionBody(
    val links: List<String>,
    val metadata: SubscriptionMetadata,
    val truncated: Boolean = false,
) {
    override fun toString(): String =
        "ParsedSubscriptionBody(linkCount=${links.size}, metadata=$metadata, truncated=$truncated)"
}

fun parseSubscriptionHeaders(headers: Map<String, List<String>>): SubscriptionMetadata {
    val normalized = LinkedHashMap<String, MutableList<String>>()
    headers.forEach { (name, values) ->
        normalized.getOrPut(name.lowercase(Locale.ROOT), ::mutableListOf).addAll(values)
    }
    val values = normalized.mapValues { (_, entries) ->
        entries.firstOrNull()?.takeCodePoints(MAX_METADATA_VALUE_CODE_POINTS)
    }
    return parseMetadataValues(values)
}

fun parseSubscriptionBody(body: String): ParsedSubscriptionBody {
    val trimmed = body.trim()
    val decoded = decodeBase64Utf8(trimmed)?.takeIf(::looksLikeLinkList)
    val content = decoded ?: body
    val links = ArrayList<String>(minOf(MAX_SUBSCRIPTION_LINKS, 16))
    val metadataValues = LinkedHashMap<String, String?>()
    var truncated = false

    content.split(Regex("\\r\\n|\\n")).forEach { rawLine ->
        val line = rawLine.trim()
        if (line.isEmpty()) return@forEach
        if (line.startsWith('#')) {
            val separator = line.indexOf(':')
            if (separator < 0) return@forEach
            val key = line.substring(1, separator).trim().lowercase(Locale.ROOT)
            if (key in SUPPORTED_METADATA_KEYS) {
                metadataValues[key] = line.substring(separator + 1)
                    .trim()
                    .takeCodePoints(MAX_METADATA_VALUE_CODE_POINTS)
            }
            return@forEach
        }
        if (links.size < MAX_SUBSCRIPTION_LINKS) {
            links += line
        } else {
            truncated = true
        }
    }

    return ParsedSubscriptionBody(
        links = links,
        metadata = parseMetadataValues(metadataValues),
        truncated = truncated,
    )
}

internal fun parseMetadataValues(values: Map<String, String?>): SubscriptionMetadata {
    fun value(name: String): String? = values[name]?.takeCodePoints(MAX_METADATA_VALUE_CODE_POINTS)

    val title = value("profile-title")?.let(::decodeOptionalBase64)
        ?.lineSequence()?.firstOrNull()
        ?.let { cleanDisplayText(it, maxCodePoints = 64) }
        ?.takeIf(String::isNotEmpty)

    val announce = value("announce")?.let(::decodeOptionalBase64)
        ?.let { cleanDisplayText(it, maxCodePoints = 1024) }
        ?.takeIf(String::isNotEmpty)

    return SubscriptionMetadata(
        title = title,
        userInfo = value("subscription-userinfo")?.let(::parseUserInfo),
        updateIntervalHours = value("profile-update-interval")
            ?.trim()?.takeIf(::isAsciiUnsignedInteger)?.toIntOrNull()
            ?.takeIf { it in 1..8760 },
        supportUrl = value("support-url")?.trim()?.takeIf { isAbsoluteWebUrl(it, "https") },
        webPageUrl = value("profile-web-page-url")?.trim()?.takeIf { isAbsoluteWebUrl(it, "https") },
        announce = announce,
    )
}

private fun decodeOptionalBase64(value: String): String? {
    val trimmed = value.trim()
    if (!trimmed.startsWith("base64:", ignoreCase = true)) return trimmed
    return decodeBase64Utf8(trimmed.substringAfter(':'))
}

private fun parseUserInfo(value: String): XraySubscriptionUserInfo? {
    var upload: Long? = null
    var download: Long? = null
    var total: Long? = null
    var expire: Long? = null

    value.split(';').forEach { pair ->
        val separator = pair.indexOf('=')
        if (separator < 0) return@forEach
        val key = pair.substring(0, separator).trim().lowercase(Locale.ROOT)
        val numberText = pair.substring(separator + 1).trim()
        if (!isAsciiUnsignedInteger(numberText)) return@forEach
        val number = numberText.toLongOrNull() ?: return@forEach
        when (key) {
            "upload" -> upload = number
            "download" -> download = number
            "total", "totl" -> total = number
            "expire" -> expire = number.takeIf { it > 0L }
        }
    }

    if (upload == null && download == null && total == null && expire == null) return null
    return XraySubscriptionUserInfo(
        uploadBytes = upload,
        downloadBytes = download,
        totalBytes = total,
        expireEpochSeconds = expire,
    )
}

private fun isAsciiUnsignedInteger(value: String): Boolean =
    value.isNotEmpty() && value.all { it in '0'..'9' }

private const val MAX_SUBSCRIPTION_LINKS = 500
private const val MAX_METADATA_VALUE_CODE_POINTS = 1024
private val SUPPORTED_METADATA_KEYS = setOf(
    "profile-title",
    "profile-update-interval",
    "subscription-userinfo",
    "support-url",
    "profile-web-page-url",
    "announce",
)
