package app.kaihi.android.core.vpn.internal

import app.kaihi.android.core.vpn.SubscriptionFetchFailure
import app.kaihi.android.core.vpn.SubscriptionFetchResult
import app.kaihi.android.core.vpn.SubscriptionFetcher
import app.kaihi.android.core.vpn.VpnPreferences
import app.kaihi.android.core.vpn.effectiveSubscriptionUserAgent
import app.kaihi.android.core.vpn.isAbsoluteWebUrl
import app.kaihi.android.core.vpn.parseSubscriptionBody
import app.kaihi.android.core.vpn.parseSubscriptionHeaders
import app.kaihi.android.platform.mode.AppModeRepository
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

internal class SubscriptionFetcherImpl @Inject constructor(
    private val httpTransport: HttpTransport,
    private val vpnPreferences: VpnPreferences,
    private val appModeRepository: AppModeRepository,
) : SubscriptionFetcher {

    override suspend fun fetch(url: String): SubscriptionFetchResult {
        if (!isAbsoluteWebUrl(url, HTTPS_SCHEME, forbidUserInfo = true)) {
            return SubscriptionFetchResult.Failure(SubscriptionFetchFailure.INSECURE_URL)
        }

        return try {
            withTimeout(FETCH_TIMEOUT_MILLIS) { fetchWithinTimeout(url) }
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

    private suspend fun fetchWithinTimeout(initialUrl: String): SubscriptionFetchResult {
        val requestHeaders = mapOf(
            USER_AGENT_HEADER to effectiveSubscriptionUserAgent(
                customValue = vpnPreferences.subscriptionUserAgent.value,
                mode = appModeRepository.mode.value,
            ),
            ACCEPT_HEADER to ANY_MEDIA_TYPE,
        )
        var currentUrl = initialUrl
        var redirectCount = 0

        while (true) {
            val response = httpTransport.get(currentUrl, requestHeaders, MAX_BODY_BYTES)
            if (response.statusCode in REDIRECT_STATUS_CODES) {
                if (redirectCount >= MAX_REDIRECTS) {
                    return SubscriptionFetchResult.Failure(SubscriptionFetchFailure.TOO_MANY_REDIRECTS)
                }
                val location = headerValue(response.headers, LOCATION_HEADER)
                    ?: return SubscriptionFetchResult.Failure(SubscriptionFetchFailure.HTTP_STATUS)
                val resolvedUrl = resolveRedirect(currentUrl, location)
                    ?: return SubscriptionFetchResult.Failure(SubscriptionFetchFailure.HTTP_STATUS)
                if (!isAbsoluteWebUrl(resolvedUrl, HTTPS_SCHEME, forbidUserInfo = true)) {
                    return SubscriptionFetchResult.Failure(SubscriptionFetchFailure.INSECURE_URL)
                }
                currentUrl = resolvedUrl
                redirectCount++
                continue
            }

            if (response.statusCode != HTTP_OK) {
                return SubscriptionFetchResult.Failure(SubscriptionFetchFailure.HTTP_STATUS)
            }
            if (response.body.size > MAX_BODY_BYTES) {
                return SubscriptionFetchResult.Failure(SubscriptionFetchFailure.TOO_LARGE)
            }

            val body = decodeStrictUtf8(response.body)
                ?: return SubscriptionFetchResult.Failure(SubscriptionFetchFailure.UNSUPPORTED_FORMAT)
            if (isUnsupportedFormat(response.headers, body)) {
                return SubscriptionFetchResult.Failure(SubscriptionFetchFailure.UNSUPPORTED_FORMAT)
            }

            val parsedBody = parseSubscriptionBody(body)
            if (parsedBody.links.isEmpty()) {
                return SubscriptionFetchResult.Failure(SubscriptionFetchFailure.EMPTY)
            }
            return SubscriptionFetchResult.Success(
                links = parsedBody.links,
                metadata = parsedBody.metadata.overriddenBy(parseSubscriptionHeaders(response.headers)),
                truncated = parsedBody.truncated,
            )
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
        if (contentType?.contains("json") == true || contentType?.contains("yaml") == true) {
            return true
        }
        val trimmed = body.trimStart()
        return trimmed.startsWith("{") || trimmed.startsWith("[") ||
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
