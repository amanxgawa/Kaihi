package app.kaihi.android.core.vpn

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XraySubscription(
    val id: String,
    val name: String,
    val servers: List<XrayVpnProfile>,
    val source: XraySubscriptionSource = XraySubscriptionSource.Deployed,
) {
    companion object {
        const val IMPORTED_ID = "imported"
    }
}

@Serializable
sealed interface XraySubscriptionSource {
    @Serializable
    @SerialName("deployed")
    data object Deployed : XraySubscriptionSource

    @Serializable
    @SerialName("imported")
    data object Imported : XraySubscriptionSource

    @Serializable
    @SerialName("remote")
    data class Remote(
        val url: String,
        val title: String? = null,
        val userInfo: XraySubscriptionUserInfo? = null,
        val updateIntervalHours: Int? = null,
        val supportUrl: String? = null,
        val webPageUrl: String? = null,
        val announce: String? = null,
        val lastUpdatedMillis: Long? = null,
    ) : XraySubscriptionSource {
        override fun toString(): String =
            "Remote(url=<redacted>, title=$title, userInfo=$userInfo, " +
                "updateIntervalHours=$updateIntervalHours, supportUrl=$supportUrl, " +
                "webPageUrl=$webPageUrl, announce=$announce, lastUpdatedMillis=$lastUpdatedMillis)"
    }
}

@Serializable
data class XraySubscriptionUserInfo(
    val uploadBytes: Long? = null,
    val downloadBytes: Long? = null,
    val totalBytes: Long? = null,
    val expireEpochSeconds: Long? = null,
)
