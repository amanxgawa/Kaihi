package app.kaihi.android.core.vpn

import app.kaihi.android.core.mode.AppMode
import app.kaihi.android.core.mode.AppModeRepository
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

class SubscriptionFetcher @Inject constructor(
    private val httpTransport: HttpTransport,
    private val vpnPreferences: VpnPreferences,
    private val appModeRepository: AppModeRepository,
) {

    suspend fun fetch(url: String, nowMillis: Long): SubscriptionFetchResult {
        if (!isAbsoluteWebUrl(url, HTTPS_SCHEME, forbidUserInfo = true)) {
            return SubscriptionFetchResult.Failure(SubscriptionFetchFailure.INSECURE_URL)
        }

        return try {
            withTimeout(FETCH_TIMEOUT_MILLIS) { fetchWithinTimeout(url, nowMillis) }
        } catch (_: TimeoutCancellationException) {
            SubscriptionFetchResult.Failure(SubscriptionFetchFailure.TIMEOUT)
        } catch (_: SocketTimeoutException) {
            SubscriptionFetchResult.Failure(SubscriptionFetchFailure.TIMEOUT)
        } catch (_: IOException) {
            SubscriptionFetchResult.Failure(SubscriptionFetchFailure.NETWORK)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            SubscriptionFetchResult.Failure(SubscriptionFetchFailure.NETWORK)
        }
    }

    private suspend fun fetchWithinTimeout(initialUrl: String, nowMillis: Long): SubscriptionFetchResult {
        val customUserAgent = vpnPreferences.subscriptionUserAgent.value
            ?.takeIf {
                appModeRepository.mode.value == AppMode.ADVANCED &&
                    isValidSubscriptionUserAgent(it)
            }
        if (customUserAgent != null) {
            return fetchAttempt {
                fetchAccepted(initialUrl, customUserAgent, nowMillis)
            }
        }

        val jsonResult = fetchAttempt {
            fetchXrayJson(initialUrl, JSON_USER_AGENT, nowMillis)
        }
        if (jsonResult is SubscriptionFetchResult.Success ||
            (jsonResult as? SubscriptionFetchResult.Failure)?.reason == SubscriptionFetchFailure.INSECURE_URL
        ) {
            return jsonResult
        }

        return fetchAttempt {
            fetchAccepted(
                initialUrl = initialUrl,
                userAgent = effectiveSubscriptionUserAgent(null, appModeRepository.mode.value),
                nowMillis = nowMillis,
            )
        }
    }

    private suspend fun fetchAttempt(block: suspend () -> SubscriptionFetchResult): SubscriptionFetchResult = try {
        block()
    } catch (_: SocketTimeoutException) {
        SubscriptionFetchResult.Failure(SubscriptionFetchFailure.TIMEOUT)
    } catch (_: IOException) {
        SubscriptionFetchResult.Failure(SubscriptionFetchFailure.NETWORK)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        SubscriptionFetchResult.Failure(SubscriptionFetchFailure.NETWORK)
    }

    private suspend fun fetchXrayJson(
        initialUrl: String,
        userAgent: String,
        nowMillis: Long,
    ): SubscriptionFetchResult {
        return when (val response = fetchResponse(initialUrl, userAgent)) {
            is HttpResponseResult.Failure -> SubscriptionFetchResult.Failure(response.reason)
            is HttpResponseResult.Success -> {
                val body = decodeStrictUtf8(response.response.body)
                    ?: return SubscriptionFetchResult.Failure(SubscriptionFetchFailure.UNSUPPORTED_FORMAT)
                val parsed = parseXrayJsonSubscription(body, nowMillis)
                    ?: return SubscriptionFetchResult.Failure(SubscriptionFetchFailure.UNSUPPORTED_FORMAT)
                if (parsed.profiles.isEmpty()) {
                    SubscriptionFetchResult.Failure(SubscriptionFetchFailure.EMPTY)
                } else {
                    SubscriptionFetchResult.Success(
                        content = SubscriptionContent.XrayJson(parsed),
                        metadata = parseSubscriptionHeaders(response.response.headers),
                        truncated = false,
                    )
                }
            }
        }
    }

    private suspend fun fetchAccepted(
        initialUrl: String,
        userAgent: String,
        nowMillis: Long,
    ): SubscriptionFetchResult {
        val response = when (val result = fetchResponse(initialUrl, userAgent)) {
            is HttpResponseResult.Failure -> return SubscriptionFetchResult.Failure(result.reason)
            is HttpResponseResult.Success -> result.response
        }
        val body = decodeStrictUtf8(response.body)
            ?: return SubscriptionFetchResult.Failure(SubscriptionFetchFailure.UNSUPPORTED_FORMAT)
        val json = parseXrayJsonSubscription(body, nowMillis)
        if (json != null) {
            if (json.profiles.isEmpty()) return SubscriptionFetchResult.Failure(SubscriptionFetchFailure.EMPTY)
            return SubscriptionFetchResult.Success(
                content = SubscriptionContent.XrayJson(json),
                metadata = parseSubscriptionHeaders(response.headers),
                truncated = false,
            )
        }
        if (isUnsupportedFormat(response.headers, body)) {
            return SubscriptionFetchResult.Failure(SubscriptionFetchFailure.UNSUPPORTED_FORMAT)
        }

        val parsedBody = parseSubscriptionBody(body)
        if (parsedBody.links.isEmpty()) {
            return SubscriptionFetchResult.Failure(SubscriptionFetchFailure.EMPTY)
        }
        return SubscriptionFetchResult.Success(
            content = SubscriptionContent.ShareLinks(parsedBody.links),
            metadata = parsedBody.metadata.overriddenBy(parseSubscriptionHeaders(response.headers)),
            truncated = parsedBody.truncated,
        )
    }

    private suspend fun fetchResponse(initialUrl: String, userAgent: String): HttpResponseResult {
        val requestHeaders = mapOf(
            USER_AGENT_HEADER to userAgent,
            ACCEPT_HEADER to ANY_MEDIA_TYPE,
        )
        var currentUrl = initialUrl
        var redirectCount = 0

        while (true) {
            val response = httpTransport.get(currentUrl, requestHeaders, MAX_BODY_BYTES)
            if (response.statusCode in REDIRECT_STATUS_CODES) {
                if (redirectCount >= MAX_REDIRECTS) {
                    return HttpResponseResult.Failure(SubscriptionFetchFailure.TOO_MANY_REDIRECTS)
                }
                val location = headerValue(response.headers, LOCATION_HEADER)
                    ?: return HttpResponseResult.Failure(SubscriptionFetchFailure.HTTP_STATUS)
                val resolvedUrl = resolveRedirect(currentUrl, location)
                    ?: return HttpResponseResult.Failure(SubscriptionFetchFailure.HTTP_STATUS)
                if (!isAbsoluteWebUrl(resolvedUrl, HTTPS_SCHEME, forbidUserInfo = true)) {
                    return HttpResponseResult.Failure(SubscriptionFetchFailure.INSECURE_URL)
                }
                currentUrl = resolvedUrl
                redirectCount++
                continue
            }

            if (response.statusCode != HTTP_OK) {
                return HttpResponseResult.Failure(SubscriptionFetchFailure.HTTP_STATUS)
            }
            if (response.body.size > MAX_BODY_BYTES) {
                return HttpResponseResult.Failure(SubscriptionFetchFailure.TOO_LARGE)
            }
            return HttpResponseResult.Success(response)
        }
    }

    private fun resolveRedirect(currentUrl: String, location: String): String? = try {
        URI(currentUrl).resolve(location).toString()
    } catch (_: IllegalArgumentException) {
        null
    }

    private fun isUnsupportedFormat(headers: Map<String, List<String>>, body: String): Boolean {
        val contentType = headerValue(headers, CONTENT_TYPE_HEADER)
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase()
        val trimmed = body.trimStart()
        if (contentType?.contains("yaml") == true ||
            (contentType?.contains("json") == true && !trimmed.startsWith("["))
        ) {
            return true
        }
        return trimmed.startsWith("{") ||
            trimmed.startsWith("proxies:") || trimmed.startsWith("mixed-port:")
    }

    private fun headerValue(headers: Map<String, List<String>>, name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }
            ?.value
            ?.firstOrNull()

    private companion object {
        const val HTTPS_SCHEME = "https"
        const val USER_AGENT_HEADER = "User-Agent"
        const val ACCEPT_HEADER = "Accept"
        const val LOCATION_HEADER = "Location"
        const val CONTENT_TYPE_HEADER = "Content-Type"
        const val ANY_MEDIA_TYPE = "*/*"
        // Panels serve Xray JSON only to clients they recognise by User-Agent.
        const val JSON_USER_AGENT = "Happ/3.0"
        const val FETCH_TIMEOUT_MILLIS = 45_000L
        const val MAX_BODY_BYTES = 1024 * 1024
        const val MAX_REDIRECTS = 3
        const val HTTP_OK = 200
        val REDIRECT_STATUS_CODES = setOf(301, 302, 303, 307, 308)

        fun decodeStrictUtf8(bytes: ByteArray): String? = try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: Exception) {
            null
        }
    }
}

private sealed interface HttpResponseResult {
    data class Success(val response: HttpTransportResponse) : HttpResponseResult
    data class Failure(val reason: SubscriptionFetchFailure) : HttpResponseResult
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

sealed interface SubscriptionContent {
    data class ShareLinks(val links: List<String>) : SubscriptionContent {
        override fun toString(): String = "ShareLinks(linkCount=${links.size})"
    }

    data class XrayJson(val subscription: XrayJsonSubscription) : SubscriptionContent {
        override fun toString(): String =
            "XrayJson(profileCount=${subscription.profiles.size}, unsupported=${subscription.unsupported}, invalid=${subscription.invalid})"
    }
}

sealed interface SubscriptionFetchResult {
    data class Success(
        val content: SubscriptionContent,
        val metadata: SubscriptionMetadata,
        val truncated: Boolean,
    ) : SubscriptionFetchResult {
        override fun toString(): String =
            "Success(content=$content, metadataPresent=${metadata != SubscriptionMetadata()}, truncated=$truncated)"
    }

    data class Failure(val reason: SubscriptionFetchFailure) : SubscriptionFetchResult {
        override fun toString(): String = "Failure(reason=$reason)"
    }
}
