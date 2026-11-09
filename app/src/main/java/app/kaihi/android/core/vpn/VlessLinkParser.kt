package app.kaihi.android.core.vpn

import app.kaihi.android.core.ssh.isSafeHostname
import app.kaihi.android.core.vpn.spec.XhttpMode
import app.kaihi.android.core.vpn.spec.XrayFingerprint
import app.kaihi.android.core.vpn.spec.XrayFingerprintSetting
import app.kaihi.android.core.vpn.spec.XrayFlow
import app.kaihi.android.core.vpn.spec.XraySecurity
import app.kaihi.android.core.vpn.spec.XrayTransport
import java.net.URI
import java.util.Base64
import java.util.Locale

sealed interface VlessLinkResult {
    data class Success(val profile: XrayVpnProfile) : VlessLinkResult
    data class Unsupported(val reason: UnsupportedReason) : VlessLinkResult
    data class Invalid(val reason: InvalidReason) : VlessLinkResult
}

enum class UnsupportedReason {
    PROTOCOL,
    ENCRYPTION,
    INSECURE_TLS,
    NO_SECURITY,
    SECURITY,
    TRANSPORT_OPTION,
    TRANSPORT,
}

enum class InvalidReason {
    TOO_LONG,
    MALFORMED_URI,
    INVALID_ID,
    INVALID_HOST,
    INVALID_PORT,
    DUPLICATE_PARAMETER,
    INVALID_ENCODING,
    INVALID_PUBLIC_KEY,
    INVALID_SHORT_ID,
}

/** Parses a share link as untrusted input. Failure values contain no part of the input. */
fun parseVlessLink(link: String, nowMillis: Long): VlessLinkResult {
    if (link.length > MAX_VLESS_LINK_LENGTH) return VlessLinkResult.Invalid(InvalidReason.TOO_LONG)

    val scheme = SCHEME_REGEX.find(link)?.groupValues?.get(1)
        ?: return VlessLinkResult.Invalid(InvalidReason.MALFORMED_URI)
    if (!scheme.equals("vless", ignoreCase = true)) {
        return if (scheme.lowercase(Locale.ROOT) in UNSUPPORTED_PROTOCOLS) {
            VlessLinkResult.Unsupported(UnsupportedReason.PROTOCOL)
        } else {
            VlessLinkResult.Invalid(InvalidReason.MALFORMED_URI)
        }
    }

    val uri = try {
        URI(link)
    } catch (_: Exception) {
        return VlessLinkResult.Invalid(InvalidReason.MALFORMED_URI)
    }
    if (uri.isOpaque || !uri.scheme.equals("vless", ignoreCase = true)) {
        return VlessLinkResult.Invalid(InvalidReason.MALFORMED_URI)
    }

    val authority = uri.rawAuthority ?: return VlessLinkResult.Invalid(InvalidReason.MALFORMED_URI)
    val at = authority.indexOf('@')
    if (at <= 0 || at != authority.lastIndexOf('@')) {
        return VlessLinkResult.Invalid(InvalidReason.INVALID_ID)
    }
    val decodedId = decodeUriComponent(authority.substring(0, at))
        ?: return VlessLinkResult.Invalid(InvalidReason.INVALID_ENCODING)
    val id = normalizeVlessId(decodedId)
        ?: return VlessLinkResult.Invalid(InvalidReason.INVALID_ID)

    val parsedHostPort = parseHostPort(authority.substring(at + 1))
    if (parsedHostPort is HostPortParseResult.Failure) {
        return VlessLinkResult.Invalid(parsedHostPort.reason)
    }
    val (host, port) = (parsedHostPort as HostPortParseResult.Success).hostPort

    val params = parseParameters(uri.rawQuery)
    if (params is ParameterParseResult.Failure) {
        return VlessLinkResult.Invalid(params.reason)
    }
    val values = (params as ParameterParseResult.Success).values

    val encryption = values["encryption"]
    if (encryption != null && encryption != "none") {
        return VlessLinkResult.Unsupported(UnsupportedReason.ENCRYPTION)
    }

    val securityResult = parseSecurity(values, host)
    if (securityResult is SecurityParseResult.Failure) {
        return securityResult.result
    }
    val security = (securityResult as SecurityParseResult.Success).security

    val transportResult = parseTransport(values, security)
    if (transportResult is TransportParseResult.Failure) {
        return VlessLinkResult.Unsupported(transportResult.reason)
    }

    val fragment = uri.rawFragment?.let(::decodeUriComponent)
        ?: if (uri.rawFragment == null) "" else return VlessLinkResult.Invalid(InvalidReason.INVALID_ENCODING)
    val name = cleanDisplayText(fragment, maxCodePoints = 64).ifEmpty { generateNickname(host) }

    return VlessLinkResult.Success(
        XrayVpnProfile(
            id = newXrayId(),
            name = name,
            ssh = null,
            serverAddress = host,
            serverPort = port,
            uuid = id,
            security = security,
            transport = (transportResult as TransportParseResult.Success).transport,
            addedAtMillis = nowMillis,
            countryCode = null,
        ),
    )
}

private fun normalizeVlessId(value: String): String? {
    if (!value.isWellFormedUnicode()) return null
    if (CANONICAL_UUID_REGEX.matches(value)) return value.lowercase(Locale.ROOT)
    val byteCount = value.toByteArray(Charsets.UTF_8).size
    if (byteCount !in 1..30) return null
    if (value.codePoints().anyMatch {
            Character.isWhitespace(it) || Character.isSpaceChar(it) || Character.isISOControl(it)
        }
    ) return null
    return value
}

private sealed interface HostPortParseResult {
    data class Success(val hostPort: Pair<String, Int>) : HostPortParseResult
    data class Failure(val reason: InvalidReason) : HostPortParseResult
}

private fun parseHostPort(value: String): HostPortParseResult {
    val host: String
    val portText: String
    if (value.startsWith('[')) {
        val closingBracket = value.indexOf(']')
        if (closingBracket <= 1) return HostPortParseResult.Failure(InvalidReason.INVALID_HOST)
        host = value.substring(1, closingBracket)
        if (!isStrictIpv6(host)) return HostPortParseResult.Failure(InvalidReason.INVALID_HOST)
        if (closingBracket + 1 >= value.length || value[closingBracket + 1] != ':') {
            return HostPortParseResult.Failure(InvalidReason.INVALID_PORT)
        }
        portText = value.substring(closingBracket + 2)
    } else {
        val colon = value.lastIndexOf(':')
        if (colon < 0) return HostPortParseResult.Failure(InvalidReason.INVALID_PORT)
        if (colon == 0 || colon != value.indexOf(':')) {
            return HostPortParseResult.Failure(InvalidReason.INVALID_HOST)
        }
        host = value.substring(0, colon)
        portText = value.substring(colon + 1)
        if (!isAcceptedHost(host)) return HostPortParseResult.Failure(InvalidReason.INVALID_HOST)
    }
    if (portText.isEmpty() || portText.any { it !in '0'..'9' }) {
        return HostPortParseResult.Failure(InvalidReason.INVALID_PORT)
    }
    val port = portText.toIntOrNull()?.takeIf { it in 1..65535 }
        ?: return HostPortParseResult.Failure(InvalidReason.INVALID_PORT)
    return HostPortParseResult.Success(host to port)
}

private fun isAcceptedHost(host: String): Boolean = when {
    host.contains('%') -> false
    looksLikeDottedIpv4(host) -> isStrictIpv4(host)
    else -> isSafeHostname(host)
}

private fun isHostnameHost(host: String): Boolean =
    !isStrictIpv4(host) && !isStrictIpv6(host) && isSafeHostname(host)

private fun looksLikeDottedIpv4(value: String): Boolean =
    value.count { it == '.' } == 3 && IPV4_SHAPED_REGEX.matches(value)

private fun isStrictIpv4(value: String): Boolean {
    val parts = value.split('.')
    return parts.size == 4 && parts.all { part ->
        part.isNotEmpty() && part.length <= 3 && part.all { it in '0'..'9' } &&
            part.toIntOrNull()?.let { it in 0..255 } == true
    }
}

private fun isStrictIpv6(value: String): Boolean {
    if (!value.contains(':') || value.contains('%')) return false

    val compressionIndex = value.indexOf("::")
    val hasCompression = compressionIndex >= 0
    if (hasCompression && value.indexOf("::", compressionIndex + 2) >= 0) return false

    val left = if (hasCompression) value.substring(0, compressionIndex) else value
    val right = if (hasCompression) value.substring(compressionIndex + 2) else ""
    val leftGroups = parseIpv6Groups(left, allowIpv4 = !hasCompression) ?: return false
    val rightGroups = parseIpv6Groups(right, allowIpv4 = true) ?: return false
    val groups = leftGroups + rightGroups

    val groupCount = groups.sum()
    return if (hasCompression) groupCount < 8 else groupCount == 8
}

private fun parseIpv6Groups(part: String, allowIpv4: Boolean): List<Int>? {
    if (part.isEmpty()) return emptyList()
    if (part.startsWith(':') || part.endsWith(':')) return null
    val tokens = part.split(':')
    if (tokens.any(String::isEmpty)) return null

    return tokens.mapIndexed { index, token ->
        if (token.contains('.')) {
            if (!allowIpv4 || index != tokens.lastIndex || !isStrictIpv4(token)) return null
            2
        } else {
            if (token.length !in 1..4 || token.any { !it.isAsciiHexDigit() }) return null
            1
        }
    }
}

private fun Char.isAsciiHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

private sealed interface ParameterParseResult {
    data class Success(val values: Map<String, String>) : ParameterParseResult
    data class Failure(val reason: InvalidReason) : ParameterParseResult
}

private fun parseParameters(rawQuery: String?): ParameterParseResult {
    if (rawQuery == null || rawQuery.isEmpty()) return ParameterParseResult.Success(emptyMap())
    val values = LinkedHashMap<String, String>()
    val seenKeys = HashSet<String>()
    for (part in rawQuery.split('&')) {
        val equals = part.indexOf('=')
        val key = decodeUriComponent(if (equals < 0) part else part.substring(0, equals))
            ?: return ParameterParseResult.Failure(InvalidReason.INVALID_ENCODING)
        val decodedValue = decodeUriComponent(if (equals < 0) "" else part.substring(equals + 1))
            ?: return ParameterParseResult.Failure(InvalidReason.INVALID_ENCODING)
        val value = decodedValue.takeIf(String::isNotEmpty)
        if (!seenKeys.add(key)) return ParameterParseResult.Failure(InvalidReason.DUPLICATE_PARAMETER)
        value?.let { values[key] = it }
    }
    return ParameterParseResult.Success(values)
}

private sealed interface SecurityParseResult {
    data class Success(val security: XraySecurity) : SecurityParseResult
    data class Failure(val result: VlessLinkResult) : SecurityParseResult
}

private fun parseSecurity(values: Map<String, String>, host: String): SecurityParseResult {
    return when (values["security"]) {
        "reality" -> {
            val publicKey = values["pbk"]
            if (publicKey == null || !isCanonicalPublicKey(publicKey)) {
                return SecurityParseResult.Failure(VlessLinkResult.Invalid(InvalidReason.INVALID_PUBLIC_KEY))
            }
            val serverName = values["sni"]
            if (serverName == null || !isSafeHostname(serverName)) {
                return SecurityParseResult.Failure(VlessLinkResult.Invalid(InvalidReason.INVALID_HOST))
            }
            val shortId = values["sid"] ?: ""
            if (shortId.length > 16 || shortId.length % 2 != 0 || shortId.any { it.digitToIntOrNull(16) == null }) {
                return SecurityParseResult.Failure(VlessLinkResult.Invalid(InvalidReason.INVALID_SHORT_ID))
            }
            val fingerprint = values["fp"]?.let { fp ->
                XrayFingerprint.entries.firstOrNull { it.wireValue.equals(fp, ignoreCase = true) }
            }
            SecurityParseResult.Success(
                XraySecurity.Reality(
                    serverName = serverName,
                    publicKey = publicKey,
                    shortId = shortId,
                    fingerprint = XrayFingerprintSetting.Auto(lastWorking = fingerprint),
                ),
            )
        }
        "tls" -> {
            val allowInsecure = values["allowInsecure"]?.let {
                it == "1" || it.equals("true", ignoreCase = true)
            } == true
            if (allowInsecure) {
                return SecurityParseResult.Failure(VlessLinkResult.Unsupported(UnsupportedReason.INSECURE_TLS))
            }
            val serverName = values["sni"] ?: host.takeIf(::isHostnameHost)
                ?: return SecurityParseResult.Failure(VlessLinkResult.Invalid(InvalidReason.INVALID_HOST))
            if (!isSafeHostname(serverName)) {
                return SecurityParseResult.Failure(VlessLinkResult.Invalid(InvalidReason.INVALID_HOST))
            }
            SecurityParseResult.Success(XraySecurity.Tls(serverName = serverName))
        }
        null, "none" -> SecurityParseResult.Failure(VlessLinkResult.Unsupported(UnsupportedReason.NO_SECURITY))
        else -> SecurityParseResult.Failure(VlessLinkResult.Unsupported(UnsupportedReason.SECURITY))
    }
}

private fun isCanonicalPublicKey(value: String): Boolean {
    if (value.length != 43 || !BASE64_URL_NO_PADDING_REGEX.matches(value)) return false
    return try {
        val decoded = Base64.getUrlDecoder().decode(value)
        decoded.size == 32 && Base64.getUrlEncoder().withoutPadding().encodeToString(decoded) == value
    } catch (_: IllegalArgumentException) {
        false
    }
}

private sealed interface TransportParseResult {
    data class Success(val transport: XrayTransport) : TransportParseResult
    data class Failure(val reason: UnsupportedReason) : TransportParseResult
}

private fun parseTransport(values: Map<String, String>, security: XraySecurity): TransportParseResult {
    return when (val type = values["type"] ?: "tcp") {
        "xhttp" -> {
            if (values.containsKey("extra")) return TransportParseResult.Failure(UnsupportedReason.TRANSPORT_OPTION)
            val effectiveSni = when (security) {
                is XraySecurity.Reality -> security.serverName
                is XraySecurity.Tls -> security.serverName
            }
            val transportHost = values["host"]
            if (transportHost != null && transportHost != effectiveSni) {
                return TransportParseResult.Failure(UnsupportedReason.TRANSPORT_OPTION)
            }
            val path = values["path"] ?: "/"
            if (!path.startsWith('/')) return TransportParseResult.Failure(UnsupportedReason.TRANSPORT_OPTION)
            val modeValue = values["mode"] ?: "auto"
            val mode = XhttpMode.entries.firstOrNull { it.wireValue == modeValue }
                ?: return TransportParseResult.Failure(UnsupportedReason.TRANSPORT_OPTION)
            TransportParseResult.Success(XrayTransport.Xhttp(path = path, mode = mode))
        }
        "tcp", "raw" -> {
            val headerType = values["headerType"]
            if (headerType != null && headerType != "none") {
                return TransportParseResult.Failure(UnsupportedReason.TRANSPORT_OPTION)
            }
            val flow = when (val flowValue = values["flow"]) {
                null, "" -> null
                XrayFlow.VISION.wireValue -> XrayFlow.VISION
                else -> return TransportParseResult.Failure(UnsupportedReason.TRANSPORT_OPTION)
            }
            TransportParseResult.Success(XrayTransport.Raw(flow = flow))
        }
        else -> TransportParseResult.Failure(UnsupportedReason.TRANSPORT)
    }
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

private const val MAX_VLESS_LINK_LENGTH = 8192
private val SCHEME_REGEX = Regex("^([A-Za-z][A-Za-z0-9+.-]*):")
private val CANONICAL_UUID_REGEX = Regex("^[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$")
private val IPV4_SHAPED_REGEX = Regex("^[0-9.]+$")
private val BASE64_URL_NO_PADDING_REGEX = Regex("^[A-Za-z0-9_-]{43}$")
private val UNSUPPORTED_PROTOCOLS = setOf(
    "vmess", "trojan", "ss", "ssr", "shadowsocks", "hysteria", "hysteria2", "hy2", "tuic",
    "socks", "socks5", "http", "https", "wireguard", "wg",
)
