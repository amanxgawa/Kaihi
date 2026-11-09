package app.kaihi.android.core.vpn

sealed interface ImportInput {
    data class RemoteSubscription(val url: String) : ImportInput {
        override fun toString(): String = "RemoteSubscription(url=<redacted>)"
    }

    data class InsecureUrl(val url: String) : ImportInput {
        override fun toString(): String = "InsecureUrl(url=<redacted>)"
    }

    data class Links(val lines: List<String>) : ImportInput {
        override fun toString(): String = "Links(lineCount=${lines.size})"
    }

    data object Unrecognized : ImportInput
}

fun classifyImportInput(text: String): ImportInput {
    val trimmed = text.trim()
    if (isAbsoluteWebUrl(trimmed, "https", forbidUserInfo = true)) {
        return ImportInput.RemoteSubscription(trimmed)
    }
    if (isAbsoluteWebUrl(trimmed, "http", forbidUserInfo = true)) {
        return ImportInput.InsecureUrl(trimmed)
    }
    val body = parseSubscriptionBody(trimmed)
    return if (body.links.isNotEmpty()) ImportInput.Links(body.links) else ImportInput.Unrecognized
}
