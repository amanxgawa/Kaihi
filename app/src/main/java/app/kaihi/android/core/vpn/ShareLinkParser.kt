package app.kaihi.android.core.vpn

import app.kaihi.android.core.security.isSafeHostname
import app.kaihi.android.core.vpn.spec.XhttpMode
import app.kaihi.android.core.vpn.spec.XrayFingerprint
import app.kaihi.android.core.vpn.spec.XrayFingerprintSetting
import app.kaihi.android.core.vpn.spec.XrayFlow
import app.kaihi.android.core.vpn.spec.XrayProtocol
import app.kaihi.android.core.vpn.spec.XraySecurity
import app.kaihi.android.core.vpn.spec.XrayTransport
import java.net.URI
import java.util.Base64
import java.util.Locale

sealed interface ShareLinkResult {
    data class Success(val profile: XrayVpnProfile) : ShareLinkResult
    data class Unsupported(val reason: UnsupportedReason) : ShareLinkResult
    data class Invalid(val reason: InvalidReason) : ShareLinkResult
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
    INVALID_PASSWORD,
    INVALID_HOST,
    INVALID_PORT,
    DUPLICATE_PARAMETER,
    INVALID_ENCODING,
    INVALID_PUBLIC_KEY,
    INVALID_SHORT_ID,
}

/** Parses a share link as untrusted input. Failure values contain no part of the input. */
fun parseShareLink(link: String, nowMillis: Long): ShareLinkResult {
    if (link.length > MAX_SHARE_LINK_LENGTH) return ShareLinkResult.Invalid(InvalidReason.TOO_LONG)

    val scheme = SCHEME_REGEX.find(link)?.groupValues?.get(1)
        ?: return ShareLinkResult.Invalid(InvalidReason.MALFORMED_URI)
    val protocol = when {
        scheme.equals("vless", ignoreCase = true) -> XrayProtocol.VLESS
        scheme.equals("trojan", ignoreCase = true) -> XrayProtocol.TROJAN
        else -> null
    }
    if (protocol == null) {
        return if (scheme.lowercase(Locale.ROOT) in UNSUPPORTED_PROTOCOLS) {
            ShareLinkResult.Unsupported(UnsupportedReason.PROTOCOL)
        } else {
            ShareLinkResult.Invalid(InvalidReason.MALFORMED_URI)
        }
    }

    val uri = try {
        URI(link)
    } catch (_: Exception) {
        return ShareLinkResult.Invalid(InvalidReason.MALFORMED_URI)
    }
    if (uri.isOpaque || !uri.scheme.equals(scheme, ignoreCase = true)) {
        return ShareLinkResult.Invalid(InvalidReason.MALFORMED_URI)
    }

    val authority = uri.rawAuthority ?: return ShareLinkResult.Invalid(InvalidReason.MALFORMED_URI)
    val at = authority.indexOf('@')
    if (at <= 0 || at != authority.lastIndexOf('@')) {
        return ShareLinkResult.Invalid(
            if (protocol == XrayProtocol.TROJAN) InvalidReason.INVALID_PASSWORD else InvalidReason.INVALID_ID,
        )
    }
    val decodedCredential = decodeUriComponent(authority.substring(0, at))
        ?: return ShareLinkResult.Invalid(InvalidReason.INVALID_ENCODING)
    val credential = when (protocol) {
        XrayProtocol.VLESS -> normalizeVlessId(decodedCredential)
            ?: return ShareLinkResult.Invalid(InvalidReason.INVALID_ID)
        XrayProtocol.TROJAN -> normalizeTrojanPassword(decodedCredential)
            ?: return ShareLinkResult.Invalid(InvalidReason.INVALID_PASSWORD)
    }

    val parsedHostPort = parseHostPort(authority.substring(at + 1))
    if (parsedHostPort is HostPortParseResult.Failure) {
        return ShareLinkResult.Invalid(parsedHostPort.reason)
    }
    val (host, port) = (parsedHostPort as HostPortParseResult.Success).hostPort

    val params = parseParameters(uri.rawQuery)
    if (params is ParameterParseResult.Failure) {
        return ShareLinkResult.Invalid(params.reason)
    }
    val values = (params as ParameterParseResult.Success).values

    val encryption = values["encryption"].takeIf { protocol == XrayProtocol.VLESS }
    if (encryption != null && encryption != "none") {
        return ShareLinkResult.Unsupported(UnsupportedReason.ENCRYPTION)
    }

    val securityResult = parseSecurity(values, host, defaultTls = protocol == XrayProtocol.TROJAN)
    if (securityResult is SecurityParseResult.Failure) {
        return securityResult.result
    }
    val security = (securityResult as SecurityParseResult.Success).security

    if (protocol == XrayProtocol.TROJAN && !values["flow"].isNullOrEmpty()) {
        return ShareLinkResult.Unsupported(UnsupportedReason.TRANSPORT_OPTION)
    }
    val transport = when (val result = parseTransport(values, security)) {
        is TransportParseResult.Success -> result.transport
        is TransportParseResult.Failure -> return ShareLinkResult.Unsupported(result.reason)
        is TransportParseResult.Invalid -> return ShareLinkResult.Invalid(result.reason)
    }

    val fragment = uri.rawFragment?.let(::decodeUriComponent)
        ?: if (uri.rawFragment == null) "" else return ShareLinkResult.Invalid(InvalidReason.INVALID_ENCODING)
    val name = cleanDisplayText(fragment, maxCodePoints = 64).ifEmpty { generateNickname(host) }

    return ShareLinkResult.Success(
        XrayVpnProfile(
            id = newXrayId(),
            name = name,
            ssh = null,
            serverAddress = host,
            serverPort = port,
            protocol = protocol,
            credential = credential,
            security = security,
            transport = transport,
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

private fun normalizeTrojanPassword(value: String): String? {
    if (!value.isWellFormedUnicode()) return null
    if (value.toByteArray(Charsets.UTF_8).size !in 1..128) return null
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
    data class Failure(val result: ShareLinkResult) : SecurityParseResult
}

private fun parseSecurity(values: Map<String, String>, host: String, defaultTls: Boolean): SecurityParseResult {
    return when (values["security"] ?: if (defaultTls) "tls" else null) {
        "reality" -> {
            val publicKey = values["pbk"]
            if (publicKey == null || !isCanonicalPublicKey(publicKey)) {
                return SecurityParseResult.Failure(ShareLinkResult.Invalid(InvalidReason.INVALID_PUBLIC_KEY))
            }
            val serverName = values["sni"]
            if (serverName == null || !isSafeHostname(serverName)) {
                return SecurityParseResult.Failure(ShareLinkResult.Invalid(InvalidReason.INVALID_HOST))
            }
            val shortId = values["sid"] ?: ""
            if (shortId.length > 16 || shortId.length % 2 != 0 || shortId.any { it.digitToIntOrNull(16) == null }) {
                return SecurityParseResult.Failure(ShareLinkResult.Invalid(InvalidReason.INVALID_SHORT_ID))
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
                return SecurityParseResult.Failure(ShareLinkResult.Unsupported(UnsupportedReason.INSECURE_TLS))
            }
            val serverName = values["sni"] ?: host.takeIf(::isHostnameHost)
                ?: return SecurityParseResult.Failure(ShareLinkResult.Invalid(InvalidReason.INVALID_HOST))
            if (!isSafeHostname(serverName)) {
                return SecurityParseResult.Failure(ShareLinkResult.Invalid(InvalidReason.INVALID_HOST))
            }
            SecurityParseResult.Success(XraySecurity.Tls(serverName = serverName))
        }
        null, "none" -> SecurityParseResult.Failure(ShareLinkResult.Unsupported(UnsupportedReason.NO_SECURITY))
        else -> SecurityParseResult.Failure(ShareLinkResult.Unsupported(UnsupportedReason.SECURITY))
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
    data class Invalid(val reason: InvalidReason) : TransportParseResult
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
        "ws", "httpupgrade" -> {
            if (security is XraySecurity.Reality || !values["flow"].isNullOrEmpty()) {
                return TransportParseResult.Failure(UnsupportedReason.TRANSPORT_OPTION)
            }
            val path = values["path"] ?: "/"
            if (!path.startsWith('/')) return TransportParseResult.Failure(UnsupportedReason.TRANSPORT_OPTION)
            val host = values["host"]
            if (host != null && !isSafeHostname(host)) {
                return TransportParseResult.Invalid(InvalidReason.INVALID_HOST)
            }
            TransportParseResult.Success(
                if (type == "ws") XrayTransport.WebSocket(path, host) else XrayTransport.HttpUpgrade(path, host),
            )
        }
        "grpc" -> {
            if (!values["flow"].isNullOrEmpty()) return TransportParseResult.Failure(UnsupportedReason.TRANSPORT_OPTION)
            val serviceName = values["serviceName"]
            if (serviceName != null && !GRPC_SERVICE_NAME_REGEX.matches(serviceName)) {
                return TransportParseResult.Failure(UnsupportedReason.TRANSPORT_OPTION)
            }
            val multiMode = when (values["mode"]) {
                null, "gun" -> false
                "multi" -> true
                else -> return TransportParseResult.Failure(UnsupportedReason.TRANSPORT_OPTION)
            }
            val authority = values["authority"]
            if (authority != null && !isSafeHostname(authority)) {
                return TransportParseResult.Invalid(InvalidReason.INVALID_HOST)
            }
            TransportParseResult.Success(XrayTransport.Grpc(serviceName ?: "", multiMode, authority))
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

private const val MAX_SHARE_LINK_LENGTH = 8192
private val SCHEME_REGEX = Regex("^([A-Za-z][A-Za-z0-9+.-]*):")
private val CANONICAL_UUID_REGEX = Regex("^[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}$")
private val IPV4_SHAPED_REGEX = Regex("^[0-9.]+$")
private val BASE64_URL_NO_PADDING_REGEX = Regex("^[A-Za-z0-9_-]{43}$")
private val GRPC_SERVICE_NAME_REGEX = Regex("^[A-Za-z0-9._/-]{1,256}$")
private val UNSUPPORTED_PROTOCOLS = setOf(
    "vmess", "ss", "ssr", "shadowsocks", "hysteria", "hysteria2", "hy2", "tuic",
    "socks", "socks5", "http", "https", "wireguard", "wg",
)
