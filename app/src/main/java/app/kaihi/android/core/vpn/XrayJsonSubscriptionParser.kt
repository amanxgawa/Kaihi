package app.kaihi.android.core.vpn

import app.kaihi.android.core.vpn.spec.XrayProtocol
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class XrayJsonSubscription(
    val profiles: List<XrayVpnProfile>,
    val unsupported: Int,
    val invalid: Int,
) {
    override fun toString(): String =
        "XrayJsonSubscription(profileCount=${profiles.size}, unsupported=$unsupported, invalid=$invalid)"
}

/** Returns null when [body] is not a bounded, top-level Xray JSON subscription array. */
fun parseXrayJsonSubscription(body: String, nowMillis: Long): XrayJsonSubscription? {
    if (!body.trimStart().startsWith('[') || hasExcessiveJsonDepth(body)) return null
    val root = try {
        Json.parseToJsonElement(body)
    } catch (_: SerializationException) {
        return null
    } catch (_: IllegalArgumentException) {
        return null
    }
    val elements = root as? JsonArray ?: return null

    val profiles = ArrayList<XrayVpnProfile>()
    var unsupported = 0
    var invalid = 0
    elements.take(MAX_JSON_SUBSCRIPTION_ELEMENTS).forEach { element ->
        val config = element as? JsonObject
        if (config == null) {
            invalid++
            return@forEach
        }

        val endpoints = ArrayList<XrayEndpoint>()
        var hadInvalidOutbound = false
        val outbounds = config["outbounds"] as? JsonArray
        outbounds?.take(MAX_JSON_OUTBOUNDS)?.forEach outboundsLoop@{ outboundElement ->
            val outbound = outboundElement as? JsonObject ?: return@outboundsLoop
            val protocol = when (outbound.string("protocol")) {
                "vless" -> XrayProtocol.VLESS
                "trojan" -> XrayProtocol.TROJAN
                else -> return@outboundsLoop
            }
            when (val result = parseJsonOutbound(outbound, protocol)) {
                null -> Unit
                is EndpointParseResult.Success -> {
                    if (endpoints.size < XrayVpnProfile.MAX_ENDPOINTS &&
                        endpoints.none { it.isSameServerAs(result.endpoint) }
                    ) {
                        endpoints += result.endpoint
                    }
                }
                is EndpointParseResult.Failure -> {
                    if (result.result is ShareLinkResult.Invalid) hadInvalidOutbound = true
                }
            }
        }

        if (endpoints.isEmpty()) {
            if (hadInvalidOutbound) invalid++ else unsupported++
        } else {
            val firstAddress = endpoints.first().address
            val name = config.string("remarks")
                ?.let { cleanDisplayText(it, maxCodePoints = 64) }
                ?.takeIf(String::isNotEmpty)
                ?: generateNickname(firstAddress)
            profiles += XrayVpnProfile(
                id = newXrayId(),
                name = name,
                endpoints = endpoints,
                addedAtMillis = nowMillis,
                countryCode = null,
            )
        }
    }
    return XrayJsonSubscription(profiles, unsupported, invalid)
}

private fun parseJsonOutbound(outbound: JsonObject, protocol: XrayProtocol): EndpointParseResult? {
    if (outbound.containsKey("proxySettings")) return null
    val streamSettings = outbound["streamSettings"] as? JsonObject
    if (streamSettings?.objectValue("sockopt")?.containsKey("dialerProxy") == true) return null
    val realitySettings = streamSettings?.objectValue("realitySettings")
    if (realitySettings?.containsKey("mldsa65Verify") == true) return null

    val settings = outbound.objectValue("settings") ?: return null
    val server = when (protocol) {
        XrayProtocol.VLESS -> settings.singleObject("vnext")
        XrayProtocol.TROJAN -> settings.singleObject("servers")
    } ?: return null
    val credentialOwner = if (protocol == XrayProtocol.VLESS) {
        server.singleObject("users") ?: return null
    } else {
        server
    }

    val values = LinkedHashMap<String, String>()
    val host = server.string("address") ?: ""
    val credential = credentialOwner.string(if (protocol == XrayProtocol.VLESS) "id" else "password") ?: ""
    val port = server.integer("port") ?: 0

    streamSettings?.string("network")?.let { values["type"] = it }
    streamSettings?.string("security")?.let { values["security"] = it }
    if (protocol == XrayProtocol.VLESS) {
        credentialOwner.string("encryption")?.let { values["encryption"] = it }
        credentialOwner.string("flow")?.let { values["flow"] = it }
    }

    when (values["security"]) {
        "reality" -> realitySettings?.apply {
            string("serverName")?.let { values["sni"] = it }
            string("publicKey")?.let { values["pbk"] = it }
            string("shortId")?.let { values["sid"] = it }
            string("fingerprint")?.let { values["fp"] = it }
        }
        "tls" -> streamSettings?.objectValue("tlsSettings")?.apply {
            string("serverName")?.let { values["sni"] = it }
            string("fingerprint")?.let { values["fp"] = it }
            if (boolean("allowInsecure") == true) values["allowInsecure"] = "1"
        }
    }

    when (values["type"] ?: "tcp") {
        "tcp", "raw" -> {
            val settingsName = if (values["type"] == "raw") "rawSettings" else "tcpSettings"
            streamSettings?.objectValue(settingsName)
                ?.objectValue("header")
                ?.string("type")
                ?.let { values["headerType"] = it }
        }
        "xhttp" -> streamSettings?.objectValue("xhttpSettings")?.apply {
            string("path")?.let { values["path"] = it }
            string("host")?.let { values["host"] = it }
            string("mode")?.let { values["mode"] = it }
            if (containsKey("extra")) values["extra"] = "present"
        }
        "ws" -> streamSettings?.objectValue("wsSettings")?.apply {
            string("path")?.let { values["path"] = it }
            (string("host") ?: objectValue("headers")?.string("Host"))
                ?.let { values["host"] = it }
        }
        "httpupgrade" -> streamSettings?.objectValue("httpupgradeSettings")?.apply {
            string("path")?.let { values["path"] = it }
            string("host")?.let { values["host"] = it }
        }
        "grpc" -> streamSettings?.objectValue("grpcSettings")?.apply {
            string("serviceName")?.let { values["serviceName"] = it }
            string("authority")?.let { values["authority"] = it }
            if (boolean("multiMode") == true) values["mode"] = "multi"
        }
    }

    return parseEndpoint(
        protocol = protocol,
        credential = credential,
        host = host,
        port = port,
        values = values,
    )
}

private fun JsonObject.singleObject(name: String): JsonObject? {
    val array = this[name] as? JsonArray ?: return null
    if (array.size != 1) return null
    return array.singleOrNull() as? JsonObject
}

private fun JsonObject.objectValue(name: String): JsonObject? = this[name] as? JsonObject

private fun JsonObject.string(name: String): String? {
    val primitive = this[name] as? JsonPrimitive ?: return null
    if (!primitive.isString) return null
    return primitive.content.takeIf(String::isNotEmpty)
}

private fun JsonObject.integer(name: String): Int? {
    val primitive = this[name] as? JsonPrimitive ?: return null
    if (primitive.isString || !JSON_INTEGER_REGEX.matches(primitive.content)) return null
    return primitive.content.toIntOrNull()
}

private fun JsonObject.boolean(name: String): Boolean? {
    val primitive = this[name] as? JsonPrimitive ?: return null
    if (primitive.isString) return null
    return when (primitive.content) {
        "true" -> true
        "false" -> false
        else -> null
    }
}

private fun hasExcessiveJsonDepth(body: String): Boolean {
    var depth = 0
    var inString = false
    var escaped = false
    for (char in body) {
        if (inString) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
        } else {
            when (char) {
                '"' -> inString = true
                '[', '{' -> {
                    depth++
                    if (depth > MAX_JSON_DEPTH) return true
                }
                ']', '}' -> depth--
            }
        }
    }
    return false
}

private const val MAX_JSON_DEPTH = 32
private const val MAX_JSON_SUBSCRIPTION_ELEMENTS = 256
private const val MAX_JSON_OUTBOUNDS = 64
private val JSON_INTEGER_REGEX = Regex("^-?(?:0|[1-9][0-9]*)$")
