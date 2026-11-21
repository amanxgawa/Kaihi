package app.kaihi.android.feature.importing.impl.ui.mvi

import app.kaihi.android.core.vpn.XrayVpnProfile
import app.kaihi.android.feature.importing.impl.domain.ImportPreparation
import app.kaihi.android.feature.importing.impl.domain.ImportPreview
import app.kaihi.android.feature.importing.impl.domain.cleanUntrustedText
import app.kaihi.android.feature.importing.impl.domain.countryCodeFromFlag
import app.kaihi.android.feature.importing.impl.ui.model.AlreadyAddedUi
import app.kaihi.android.feature.importing.impl.ui.model.CountryGroupUi
import app.kaihi.android.feature.importing.impl.ui.model.ImportPreviewUi
import app.kaihi.android.feature.importing.impl.ui.model.PreviewLevel

internal fun ImportPreview.toUi(nowMillis: Long): ImportPreviewUi {
    val kind = when (this) {
        is ImportPreview.Links -> if (profiles.size == 1) {
            ImportPreviewUi.Kind.SINGLE_SERVER
        } else {
            ImportPreviewUi.Kind.SERVERS
        }
        is ImportPreview.Subscription -> ImportPreviewUi.Kind.SUBSCRIPTION
    }
    val singleProfile = profiles.singleOrNull().takeIf { kind == ImportPreviewUi.Kind.SINGLE_SERVER }
    val metadata = (this as? ImportPreview.Subscription)?.metadata
    val userInfo = metadata?.userInfo
    val usedBytes = userInfo?.usedBytes()
    val totalBytes = userInfo?.totalBytes
    val expiresAtMillis = userInfo?.expireEpochSeconds?.times(1000L)

    return ImportPreviewUi(
        kind = kind,
        title = when (this) {
            is ImportPreview.Links -> if (kind == ImportPreviewUi.Kind.SINGLE_SERVER) {
                cleanUntrustedText(profiles.single().name, maxCodePoints = 64)
            } else {
                ""
            }
            is ImportPreview.Subscription -> cleanUntrustedText(this.metadata.title ?: host, maxCodePoints = 64)
        },
        domain = (this as? ImportPreview.Subscription)?.host?.let {
            cleanUntrustedText(it, maxCodePoints = 253)
        },
        singleServerCountryCode = singleProfile?.let { it.countryCode ?: countryCodeFromFlag(it.name) },
        usedBytes = usedBytes,
        totalBytes = totalBytes,
        trafficLevel = when {
            totalBytes == null || totalBytes <= 0L -> PreviewLevel.NORMAL
            (usedBytes ?: 0L) >= totalBytes -> PreviewLevel.ERROR
            (usedBytes ?: 0L).toDouble() >= totalBytes.toDouble() * 0.9 -> PreviewLevel.WARNING
            else -> PreviewLevel.NORMAL
        },
        expiresAtMillis = expiresAtMillis,
        expiryLevel = when {
            expiresAtMillis == null -> PreviewLevel.NORMAL
            expiresAtMillis <= nowMillis -> PreviewLevel.ERROR
            expiresAtMillis <= nowMillis + THREE_DAYS_MILLIS -> PreviewLevel.WARNING
            else -> PreviewLevel.NORMAL
        },
        announce = metadata?.announce?.let { cleanUntrustedText(it, maxCodePoints = 300, keepNewlines = true) },
        serverCount = profiles.size,
        groups = profiles.toCountryGroups(),
        skippedCount = counts.unsupported + counts.invalid,
        alreadyAddedCount = if (this is ImportPreview.Links) counts.duplicates else 0,
        truncated = counts.truncated,
    )
}

internal fun ImportPreparation.AlreadyAdded.toUi(): AlreadyAddedUi = AlreadyAddedUi(
    name = cleanUntrustedText(name, maxCodePoints = 64),
    firstServerId = firstServerId,
)

private fun List<XrayVpnProfile>.toCountryGroups(): List<CountryGroupUi> {
    val namesByCountry = linkedMapOf<String?, MutableList<String>>()
    forEach { profile ->
        val countryCode = profile.countryCode ?: countryCodeFromFlag(profile.name)
        namesByCountry.getOrPut(countryCode, ::mutableListOf)
            .add(cleanUntrustedText(profile.name, maxCodePoints = 64))
    }
    return namesByCountry.entries
        .sortedWith(
            compareBy<Map.Entry<String?, MutableList<String>>> { it.key == null }
                .thenByDescending { it.value.size }
                .thenBy { it.key.orEmpty() },
        )
        .map { (countryCode, serverNames) -> CountryGroupUi(countryCode, serverNames) }
}

private const val THREE_DAYS_MILLIS = 3L * 24 * 60 * 60 * 1000
