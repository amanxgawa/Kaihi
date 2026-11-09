package app.kaihi.android.core.vpn

interface SubscriptionFetcher {
    suspend fun fetch(url: String): SubscriptionFetchResult
}

enum class SubscriptionFetchFailure {
    INSECURE_URL,
    NETWORK,
    TIMEOUT,
    HTTP_STATUS,
    TOO_LARGE,
    TOO_MANY_REDIRECTS,
    UNSUPPORTED_FORMAT,
    EMPTY,
}

sealed interface SubscriptionFetchResult {
    data class Success(
        val links: List<String>,
        val metadata: SubscriptionMetadata,
        val truncated: Boolean,
    ) : SubscriptionFetchResult {
        override fun toString(): String =
            "Success(linkCount=${links.size}, metadataPresent=${metadata != SubscriptionMetadata()}, truncated=$truncated)"
    }

    data class Failure(val reason: SubscriptionFetchFailure) : SubscriptionFetchResult {
        override fun toString(): String = "Failure(reason=$reason)"
    }
}
