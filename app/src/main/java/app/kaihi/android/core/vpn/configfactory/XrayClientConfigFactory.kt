package app.kaihi.android.core.vpn.configfactory

import app.kaihi.android.BuildConfig
import app.kaihi.android.core.vpn.SocksEndpoint
import app.kaihi.android.core.vpn.FingerprintCandidate
import app.kaihi.android.core.vpn.TunnelSettings
import app.kaihi.android.core.vpn.XrayEndpoint
import app.kaihi.android.core.vpn.XrayRuntimeConfig
import app.kaihi.android.core.vpn.XrayVpnProfile
import app.kaihi.android.core.vpn.configfactory.XrayClientConfigFactory.createRuntimeConfig
import app.kaihi.android.core.vpn.realityFingerprint
import app.kaihi.android.core.vpn.withRealityFingerprint
import app.kaihi.android.core.vpn.ProbeTargets
import app.kaihi.android.core.vpn.spec.BurstObservatoryObject
import app.kaihi.android.core.vpn.spec.DnsObject
import app.kaihi.android.core.vpn.spec.FakeDnsObject
import app.kaihi.android.core.vpn.spec.InboundObject
import app.kaihi.android.core.vpn.spec.LogObject
import app.kaihi.android.core.vpn.spec.MuxObject
import app.kaihi.android.core.vpn.spec.OutboundObject
import app.kaihi.android.core.vpn.spec.PingConfigObject
import app.kaihi.android.core.vpn.spec.PolicyObject
import app.kaihi.android.core.vpn.spec.SniffingObject
import app.kaihi.android.core.vpn.spec.SystemPolicyObject
import app.kaihi.android.core.vpn.spec.VlessOutboundSettings
import app.kaihi.android.core.vpn.spec.VlessVnext
import app.kaihi.android.core.vpn.spec.VlessVnextUser
import app.kaihi.android.core.vpn.spec.TrojanOutboundSettings
import app.kaihi.android.core.vpn.spec.TrojanServer
import app.kaihi.android.core.vpn.spec.XrayConfig
import app.kaihi.android.core.vpn.spec.XrayFlow
import app.kaihi.android.core.vpn.spec.XrayFingerprint
import app.kaihi.android.core.vpn.spec.XrayFingerprintSetting
import app.kaihi.android.core.vpn.spec.XrayProtocol
import app.kaihi.android.core.vpn.spec.XrayTransport
import app.kaihi.android.core.vpn.spec.candidates
import app.kaihi.android.core.vpn.spec.preferred
import app.kaihi.android.core.vpn.spec.toJsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object XrayClientConfigFactory {
    private const val SOCKS_IN_TAG = "socks-in"
    // The balancer and observatory select by this prefix; no non-proxy outbound may start with it.
    private const val PROXY_OUTBOUND_TAG = "proxy"
    // Probe multiple endpoints more often so the balancer notices a dead endpoint sooner.
    private const val SINGLE_ENDPOINT_PROBE_INTERVAL = "1m"
    private const val MULTI_ENDPOINT_PROBE_INTERVAL = "30s"
    private const val LOOPBACK = "127.0.0.1"
    private const val SOCKS_PORT = 10_808
    private const val FAKE_DNS_MARKER = "fakedns"
    private const val FAKE_DNS_POOL = "198.18.0.0/15"
    private const val FAKE_DNS_POOL_SIZE = 65_535
    private const val SOCKS_CREDENTIAL_BYTES = 18
    // "reject" is Xray's documented default for MuxObject.xudpProxyUDP443; write it explicitly
    // so QUIC stays blocked, consistent with the UDP/443 blackhole rule.
    private const val MUX_XUDP_PROXY_UDP_443 = "reject"
    // 128 is the maximum MuxObject.concurrency per Xray's outbound docs, also used as the xmux cap.
    private const val MAX_MUX_CONCURRENCY = 128
    private const val MIN_MUX_CONCURRENCY = 1
    // Under Vision the server breaks Mux connections that carry TCP, so -1 keeps TCP off Mux and only XUDP is muxed.
    private const val VISION_TCP_MUX_DISABLED = -1
    private const val HEX_DIGITS = "0123456789abcdef"
    // Used only as hashed input; this credential is never used to run Xray.
    private val PLACEHOLDER_CREDENTIAL = SocksCredential(user = "kaihi", pass = "settings-key")

    fun createRuntimeConfig(profile: XrayVpnProfile, settings: TunnelSettings): XrayRuntimeConfig {
        val effective = resolve(profile, settings)
        // See SocksEndpoint for why this can't be "noauth".
        val socksCredential = generateSocksCredential()
        val fingerprintSetting = effective.profile.realityFingerprint
        val fingerprint = fingerprintSetting?.preferred()
        val probeUrl = ProbeTargets.urlForWholeSession()
        val candidates = (fingerprintSetting as? XrayFingerprintSetting.Auto)
            ?.candidates()
            ?.map { candidate ->
                FingerprintCandidate(
                    fingerprint = candidate,
                    rawJson = build(effective.profile, socksCredential, effective.ipv6Enabled, probeUrl, candidate).toJson(),
                )
            }
            .orEmpty()
        return XrayRuntimeConfig(
            rawJson = candidates.firstOrNull()?.rawJson
                ?: build(
                    profile = effective.profile,
                    socksCredential = socksCredential,
                    ipv6Enabled = effective.ipv6Enabled,
                    probeUrl = probeUrl,
                    fingerprint = fingerprint ?: XrayFingerprint.FIREFOX,
                ).toJson(),
            socksEndpoint = SocksEndpoint(LOOPBACK, SOCKS_PORT, socksCredential.user, socksCredential.pass),
            serverId = effective.profile.id,
            ipv6Enabled = effective.ipv6Enabled,
            settingsKey = settingsKey(effective),
            fingerprintCandidates = candidates,
            fingerprint = fingerprint,
            excludedPackages = effective.excludedPackages.sorted(),
        )
    }

    fun settingsKey(profile: XrayVpnProfile, settings: TunnelSettings): String =
        settingsKey(resolve(profile, settings))

    private fun settingsKey(effective: EffectiveTunnelConfig): String {
        val profile = effective.profile
        val setting = profile.fingerprintSettingOrNull()
        val fingerprint = when (setting) {
            null, is XrayFingerprintSetting.Auto -> XrayFingerprintSetting.Auto().preferred()
            is XrayFingerprintSetting.Fixed -> setting.preferred()
        }
        val config = build(
            profile = profile,
            socksCredential = PLACEHOLDER_CREDENTIAL,
            ipv6Enabled = effective.ipv6Enabled,
            probeUrl = ProbeTargets.urlForSettingsKey(),
            fingerprint = fingerprint,
        ).toJson() + if (setting is XrayFingerprintSetting.Auto) "\nfingerprint=auto" else ""
        val settingsKeyInput = if (effective.excludedPackages.isEmpty()) {
            config
        } else {
            "$config\nexcludedPackages=" + effective.excludedPackages.sorted().joinToString("\n")
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(
            settingsKeyInput.toByteArray(Charsets.UTF_8)
        )
        return buildString(digest.size * 2) {
            digest.forEach { byte ->
                val unsigned = byte.toInt() and 0xff
                append(HEX_DIGITS[unsigned ushr 4])
                append(HEX_DIGITS[unsigned and 0x0f])
            }
        }
    }

    /**
     * The config [createRuntimeConfig] would run, for previewing or testing the generated JSON
     * without threading a throwaway SOCKS credential through.
     */
    fun build(profile: XrayVpnProfile, ipv6Enabled: Boolean = false): XrayConfig =
        build(profile, generateSocksCredential(), ipv6Enabled, profile.fingerprintForConfig())

    private fun build(
        profile: XrayVpnProfile,
        socksCredential: SocksCredential,
        ipv6Enabled: Boolean,
        fingerprint: XrayFingerprint,
    ): XrayConfig = build(
        profile = profile,
        socksCredential = socksCredential,
        ipv6Enabled = ipv6Enabled,
        probeUrl = ProbeTargets.urlForWholeSession(),
        fingerprint = fingerprint,
    )

    private fun build(
        profile: XrayVpnProfile,
        socksCredential: SocksCredential,
        ipv6Enabled: Boolean,
        probeUrl: String,
        fingerprint: XrayFingerprint,
    ): XrayConfig {
        require(profile.endpoints.isNotEmpty()) { "profile has no endpoints" }
        val proxyOutbounds = profile.endpoints.take(XrayVpnProfile.MAX_ENDPOINTS)
            .mapIndexed { index, endpoint -> proxyOutbound(endpoint, proxyTag(index), profile, fingerprint) }

        return XrayConfig(
            log = LogObject(loglevel = if (BuildConfig.DEBUG) "debug" else "warning"),
            stats = JsonObject(emptyMap()),
            policy = PolicyObject(
                system = SystemPolicyObject(
                    statsInboundUplink = true,
                    statsInboundDownlink = true,
                    statsOutboundUplink = true,
                    statsOutboundDownlink = true,
                ),
            ),
            // FakeDNS answers locally so DNS never needs a UDP round trip through the tunnel;
            // sniffing on socks-in recovers the real domain from the fake IP.
            dns = DnsObject(servers = listOf(JsonPrimitive(FAKE_DNS_MARKER))),
            fakedns = listOf(FakeDnsObject(ipPool = FAKE_DNS_POOL, poolSize = FAKE_DNS_POOL_SIZE)),
            inbounds = listOf(socksInbound(socksCredential)),
            outbounds = proxyOutbounds + listOf(
                OutboundObject(protocol = "freedom", tag = "direct"),
                OutboundObject(protocol = "blackhole", tag = "block"),
                OutboundObject(protocol = "dns", tag = "dns-out"),
            ),
            routing = XrayRoutingFactory.build(
                socksInboundTag = SOCKS_IN_TAG,
                proxyOutboundTag = PROXY_OUTBOUND_TAG,
                ipv6Enabled = ipv6Enabled,
            ),
            // Gives xray's own accounting a health signal for the stuck-dial failure mode
            // (XTLS/Xray-core#6590). See XrayHealthWatchdog for the actual detection.
            burstObservatory = BurstObservatoryObject(
                subjectSelector = listOf(PROXY_OUTBOUND_TAG),
                pingConfig = PingConfigObject(
                    // Fixed for the session because xray reads it once, but drawn from a pool so
                    // that it is not the same name on every install. See ProbeTargets.
                    destination = probeUrl,
                    interval = if (profile.endpoints.size > 1) {
                        MULTI_ENDPOINT_PROBE_INTERVAL
                    } else {
                        SINGLE_ENDPOINT_PROBE_INTERVAL
                    },
                    sampling = 4,
                    timeout = "3s",
                ),
            ),
        )
    }

    private fun proxyTag(index: Int): String =
        if (index == 0) PROXY_OUTBOUND_TAG else "$PROXY_OUTBOUND_TAG-${index + 1}"

    private fun proxyOutbound(
        endpoint: XrayEndpoint,
        tag: String,
        profile: XrayVpnProfile,
        fingerprint: XrayFingerprint,
    ): OutboundObject = OutboundObject(
        protocol = when (endpoint.protocol) {
            XrayProtocol.VLESS -> "vless"
            XrayProtocol.TROJAN -> "trojan"
        },
        tag = tag,
        settings = when (endpoint.protocol) {
            XrayProtocol.VLESS -> VlessOutboundSettings(
                vnext = listOf(
                    VlessVnext(
                        address = endpoint.address,
                        port = endpoint.port,
                        users = listOf(
                            // Vision only applies to raw TCP. It silently breaks XHTTP despite
                            // the TCP handshake succeeding, so it is only set for Raw transport.
                            VlessVnextUser(
                                id = endpoint.credential,
                                encryption = "none",
                                flow = (endpoint.transport as? XrayTransport.Raw)?.flow?.wireValue,
                            ),
                        ),
                    ),
                ),
            ).toJsonObject()
            XrayProtocol.TROJAN -> TrojanOutboundSettings(
                servers = listOf(
                    TrojanServer(
                        address = endpoint.address,
                        port = endpoint.port,
                        password = endpoint.credential,
                    ),
                ),
            ).toJsonObject()
        },
        streamSettings = XrayTransportFactory.streamSettings(
            transport = endpoint.transport,
            security = endpoint.security,
            fingerprint = fingerprint,
            xmuxMaxConcurrency = if (profile.muxEnabled) {
                profile.muxConcurrency.coerceIn(MIN_MUX_CONCURRENCY, MAX_MUX_CONCURRENCY)
            } else {
                MIN_MUX_CONCURRENCY
            },
            fragmentEnabled = profile.fragmentEnabled,
        ),
        // XHTTP has its own client multiplexer (xmux), so it must never use Mux.Cool.
        mux = muxFor(profile, endpoint),
    )

    /** "password" auth, not "noauth". See SocksEndpoint for why. */
    private fun socksInbound(credential: SocksCredential) = InboundObject(
        listen = LOOPBACK,
        port = intPrimitive(SOCKS_PORT),
        protocol = "socks",
        tag = SOCKS_IN_TAG,
        settings = JsonObject(
            mapOf(
                "auth" to JsonPrimitive("password"),
                "accounts" to JsonArray(
                    listOf(
                        JsonObject(
                            mapOf(
                                "user" to JsonPrimitive(credential.user),
                                "pass" to JsonPrimitive(credential.pass),
                            )
                        )
                    )
                ),
                "udp" to JsonPrimitive(true),
            )
        ),
        sniffing = SniffingObject(
            enabled = true,
            destOverride = listOf(FAKE_DNS_MARKER, "http", "tls"),
        ),
    )

    private fun generateSocksCredential(): SocksCredential {
        val bytes = ByteArray(SOCKS_CREDENTIAL_BYTES)
        SecureRandom().nextBytes(bytes)
        val secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        return SocksCredential(user = "kaihi", pass = secret)
    }

    private fun muxFor(profile: XrayVpnProfile, endpoint: XrayEndpoint): MuxObject? {
        val raw = endpoint.transport as? XrayTransport.Raw ?: return null
        if (!profile.muxEnabled) return null

        val concurrency = profile.muxConcurrency.coerceIn(MIN_MUX_CONCURRENCY, MAX_MUX_CONCURRENCY)
        return MuxObject(
            enabled = true,
            concurrency = if (raw.flow == XrayFlow.VISION) {
                VISION_TCP_MUX_DISABLED
            } else {
                concurrency
            },
            xudpConcurrency = concurrency,
            xudpProxyUDP443 = MUX_XUDP_PROXY_UDP_443,
        )
    }

    private data class SocksCredential(val user: String, val pass: String)

    private fun XrayVpnProfile.fingerprintSettingOrNull() = realityFingerprint

    private fun XrayVpnProfile.fingerprintForConfig() =
        fingerprintSettingOrNull()?.preferred() ?: XrayFingerprintSetting.Auto().preferred()

    private fun resolve(profile: XrayVpnProfile, settings: TunnelSettings): EffectiveTunnelConfig {
        if (settings.advancedMode) {
            return EffectiveTunnelConfig(profile, settings.ipv6Enabled, settings.excludedPackages)
        }

        val resolvedProfile = profile.withRealityFingerprint { saved ->
            when (saved) {
                is XrayFingerprintSetting.Auto -> saved
                is XrayFingerprintSetting.Fixed -> XrayFingerprintSetting.Auto()
            }
        }
        return EffectiveTunnelConfig(
            profile = resolvedProfile.copy(
                muxEnabled = false,
                fragmentEnabled = false,
            ),
            ipv6Enabled = false,
            excludedPackages = settings.excludedPackages,
        )
    }

    private data class EffectiveTunnelConfig(
        val profile: XrayVpnProfile,
        val ipv6Enabled: Boolean,
        val excludedPackages: Set<String>,
    )
}
