package app.kaihi.android.core.vpn

import app.kaihi.android.core.vpn.spec.XrayFingerprint
import kotlinx.serialization.Serializable

@Serializable
data class FingerprintCandidate(
    val fingerprint: XrayFingerprint,
    val rawJson: String,
) {
    override fun toString(): String = "FingerprintCandidate(fingerprint=${fingerprint.wireValue})"
}

@Serializable
data class XrayRuntimeConfig(
    val rawJson: String,
    val socksEndpoint: SocksEndpoint,
    val statusIntervalMillis: Long = 1_000L,
    // Nullable only so a restart intent scheduled by an older build still decodes.
    val serverId: String? = null,
    val ipv6Enabled: Boolean = false,
    // Nullable only so a restart intent scheduled by an older build still decodes.
    val settingsKey: String? = null,
    val fingerprintCandidates: List<FingerprintCandidate> = emptyList(),
    val fingerprint: XrayFingerprint? = null,
    val excludedPackages: List<String> = emptyList(),
) {
    override fun toString(): String =
        "XrayRuntimeConfig(ipv6Enabled=$ipv6Enabled, fingerprintCandidates=${fingerprintCandidates.size}, " +
            "excludedPackages=${excludedPackages.size})"
}
