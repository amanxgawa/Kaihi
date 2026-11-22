package app.kaihi.android.core.vpn

import app.kaihi.android.core.vpn.spec.XrayFingerprintSetting
import app.kaihi.android.core.vpn.spec.XraySecurity
import kotlinx.serialization.Serializable

@Serializable
data class XrayVpnProfile(
    val id: String,
    val name: String,
    val ssh: XraySshAccess? = null,
    // Never empty; at most MAX_ENDPOINTS. Deployed servers always have exactly one.
    val endpoints: List<XrayEndpoint>,
    val addedAtMillis: Long,
    // ISO 3166-1 alpha-2.
    val countryCode: String? = null,
    val muxEnabled: Boolean = false,
    val muxConcurrency: Int = DEFAULT_MUX_CONCURRENCY,
    val fragmentEnabled: Boolean = false,
    val serverProtection: XrayServerProtection? = null,
) {
    override fun toString(): String =
        "XrayVpnProfile(id=$id, name=$name, ssh=$ssh, endpoints=$endpoints, " +
            "addedAtMillis=$addedAtMillis, countryCode=$countryCode, " +
            "muxEnabled=$muxEnabled, muxConcurrency=$muxConcurrency, fragmentEnabled=$fragmentEnabled, " +
            "serverProtection=$serverProtection)"

    companion object {
        const val DEFAULT_MUX_CONCURRENCY = 8
        const val MAX_ENDPOINTS = 16
    }
}

/** True when any endpoint of this row is the same server as any endpoint of [other]. */
fun XrayVpnProfile.sharesEndpointWith(other: XrayVpnProfile): Boolean =
    endpoints.any { endpoint -> other.endpoints.any { endpoint.isSameServerAs(it) } }

/** Same endpoints as [other], ignoring order and duplicates (compared with isSameServerAs). */
fun XrayVpnProfile.hasSameEndpointsAs(other: XrayVpnProfile): Boolean =
    endpoints.all { endpoint -> other.endpoints.any { endpoint.isSameServerAs(it) } } &&
        other.endpoints.all { endpoint -> endpoints.any { endpoint.isSameServerAs(it) } }

/** The fingerprint setting of the first REALITY endpoint, or null when no endpoint uses REALITY. */
val XrayVpnProfile.realityFingerprint: XrayFingerprintSetting?
    get() = endpoints.firstNotNullOfOrNull { (it.security as? XraySecurity.Reality)?.fingerprint }

/** Applies [transform] to the fingerprint of every REALITY endpoint; TLS endpoints are unchanged. */
fun XrayVpnProfile.withRealityFingerprint(
    transform: (XrayFingerprintSetting) -> XrayFingerprintSetting,
): XrayVpnProfile = copy(
    endpoints = endpoints.map { endpoint ->
        endpoint.copy(
            security = (endpoint.security as? XraySecurity.Reality)?.let { reality ->
                reality.copy(fingerprint = transform(reality.fingerprint))
            }
                ?: endpoint.security,
        )
    },
)

@Serializable
data class XraySshAccess(
    val host: String,
    val port: Int,
    val username: String,
    val expectedFingerprint: String? = null,
) {
    override fun toString(): String =
        "XraySshAccess(host=$host, port=$port, username=$username)"
}
