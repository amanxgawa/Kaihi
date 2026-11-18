package app.kaihi.android

/** Text another app handed to Kaihi to import, or null. Untrusted: it only pre-fills the import preview. */
internal fun incomingImportText(
    action: String?,
    dataString: String?,
    mimeType: String?,
    extraText: CharSequence?,
): String? {
    val text = when {
        action == "android.intent.action.VIEW" && dataString?.startsWith("vless://", ignoreCase = true) == true ->
            dataString
        action == "android.intent.action.SEND" && mimeType == "text/plain" -> extraText?.toString()
        else -> null
    }?.trim() ?: return null

    return text.takeIf { it.isNotEmpty() && it.length <= MAX_INCOMING_IMPORT_CHARS }
}

internal const val MAX_INCOMING_IMPORT_CHARS = 65_536
