package app.kaihi.android.feature.importing.impl.ui.model

import app.kaihi.android.platform.ui.components.SubscriptionUsageUi

internal data class CountryGroupUi(
    val countryCode: String?,
    val serverNames: List<String>,
)

internal data class ImportPreviewUi(
    val kind: Kind,
    val title: String,
    val domain: String?,
    val singleServerCountryCode: String?,
    val usage: SubscriptionUsageUi?,
    val serverCount: Int,
    val groups: List<CountryGroupUi>,
    val skippedCount: Int,
    val alreadyAddedCount: Int,
    val truncated: Boolean,
) {
    enum class Kind { SINGLE_SERVER, SERVERS, SUBSCRIPTION }
}

internal data class AlreadyAddedUi(val name: String, val firstServerId: String?)
