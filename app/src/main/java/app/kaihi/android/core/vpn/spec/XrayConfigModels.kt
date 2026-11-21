package app.kaihi.android.core.vpn.spec

import app.kaihi.android.core.vpn.XrayJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Xray-core's config schema, following the official documentation.
 *
 * Fields whose JSON shape varies by protocol or context (a port that may be an int or a range
 * string, a protocol's `settings` block) are [JsonElement] / [JsonObject] / [JsonPrimitive]
 * rather than `Any`, which kotlinx.serialization cannot serialize at all.
 */

@Serializable
data class XrayConfig(
    val env: Map<String, String>? = null,
    val log: LogObject? = null,
    val api: ApiObject? = null,
    val dns: DnsObject? = null,
    val routing: RoutingObject? = null,
    val policy: PolicyObject? = null,
    val inbounds: List<InboundObject>? = null,
    val outbounds: List<OutboundObject>? = null,
    val stats: JsonObject? = null,
    val fakedns: List<FakeDnsObject>? = null,
    val observatory: ObservatoryObject? = null,
    val burstObservatory: BurstObservatoryObject? = null,
    val version: VersionObject? = null
) {
    fun toJson(): String = XrayJson.default.encodeToString(serializer(), this)
}

@Serializable
data class LogObject(
    val access: String? = null,
    val error: String? = null,
    val loglevel: String? = null, // "debug" | "info" | "warning" | "error" | "none"
    val dnsLog: Boolean? = null,
)

@Serializable
data class ApiObject(
    val tag: String? = null,
    val listen: String? = null,
    val services: List<String>? = null
)

@Serializable
data class DnsObject(
    val hosts: Map<String, JsonElement>? = null, // Value can be a string or a list of strings
    val servers: List<JsonElement>? = null, // Plain address strings or server objects
    val tag: String? = null
)

@Serializable
data class FakeDnsObject(
    val ipPool: String? = null,
    val poolSize: Int? = null
)

@Serializable
data class RoutingObject(
    val domainStrategy: String? = null, // "AsIs" | "IPIfNonMatch" | "IPOnDemand"
    val rules: List<RuleObject>? = null,
    val balancers: List<BalancerObject>? = null
)

@Serializable
data class RuleObject(
    val type: String,
    val domain: List<String>? = null,
    val ip: List<String>? = null,
    val port: JsonPrimitive? = null, // Int or range string, e.g. "0-1023"
    val localPort: JsonPrimitive? = null,
    val network: String? = null, // "tcp" | "udp" | "tcp,udp"
    val user: List<String>? = null,
    val inboundTag: List<String>? = null,
    val protocol: List<String>? = null,
    val process: List<String>? = null,
    val outboundTag: String? = null,
    val balancerTag: String? = null
)

@Serializable
data class BalancerObject(
    val tag: String? = null,
    val selector: List<String>? = null,
    val strategy: StrategyObject? = null
)

@Serializable
data class StrategyObject(
    val type: String? = null, // "random" | "roundRobin" | "leastPing" | "leastLoad"
    val settings: StrategySettingsObject? = null
)

@Serializable
data class StrategySettingsObject(
    val expected: Int? = null,
    val maxRTT: String? = null,
    val tolerance: Float? = null,
    val baselines: List<String>? = null,
    val costs: List<CostObject>? = null
)

@Serializable
data class CostObject(
    val match: String? = null,
    val value: Float? = null
)

@Serializable
data class PolicyObject(
    val levels: Map<String, LevelPolicyObject>? = null,
    val system: SystemPolicyObject? = null
)

@Serializable
data class LevelPolicyObject(
    val handshake: Int? = null,
)

@Serializable
data class SystemPolicyObject(
    val statsInboundUplink: Boolean? = null,
    val statsInboundDownlink: Boolean? = null,
    val statsOutboundUplink: Boolean? = null,
    val statsOutboundDownlink: Boolean? = null
)

@Serializable
data class ObservatoryObject(
    val subjectSelector: List<String>? = null,
    val probeUrl: String? = null,
)

@Serializable
data class BurstObservatoryObject(
    val subjectSelector: List<String>? = null,
    val pingConfig: PingConfigObject? = null
)

@Serializable
data class PingConfigObject(
    val destination: String? = null,
    val connectivity: String? = null,
    val interval: String? = null,
    val sampling: Int? = null,
    val timeout: String? = null,
)

@Serializable
data class VersionObject(
    val min: String? = null,
    val max: String? = null
)

@Serializable
data class InboundObject(
    val listen: String? = null,
    val port: JsonPrimitive? = null, // Int or range string, e.g. "443-450"
    val protocol: String? = null,
    val settings: JsonObject? = null, // Protocol-specific inbound settings; see typed *InboundSettings helpers below
    val streamSettings: StreamSettingsObject? = null,
    val tag: String? = null,
    val sniffing: SniffingObject? = null
)

@Serializable
data class SniffingObject(
    val enabled: Boolean? = null,
    val destOverride: List<String>? = null,
)

@Serializable
data class OutboundObject(
    val protocol: String? = null,
    val settings: JsonObject? = null, // Protocol-specific outbound settings; see typed *OutboundSettings helpers below
    val tag: String? = null,
    val streamSettings: StreamSettingsObject? = null,
    val mux: MuxObject? = null,
)

@Serializable
data class MuxObject(
    val enabled: Boolean? = null,
    val concurrency: Int? = null,
    val xudpConcurrency: Int? = null,
    val xudpProxyUDP443: String? = null
)

@Serializable
data class StreamSettingsObject(
    val method: String? = null,
    val rawSettings: RawObject? = null,
    val xhttpSettings: JsonObject? = null,
    val security: String? = null, // "none" | "reality" | "tls"
    val realitySettings: RealityObject? = null,
    val tlsSettings: TlsObject? = null,
    val finalmask: FinalMaskObject? = null,
    val sockopt: SockoptObject? = null
)

@Serializable
data class RawObject(
    val acceptProxyProtocol: Boolean? = null,
    val header: JsonObject? = null // NoneHeaderObject or HttpHeaderObject shape
)

@Serializable
data class RealityObject(
    val show: Boolean? = null,
    val target: String? = null,
    val xver: Int? = null,
    val serverNames: List<String>? = null,
    val privateKey: String? = null,
    val shortIds: List<String>? = null,
    val serverName: String? = null,
    val fingerprint: String? = null,
    val password: String? = null,
    val shortId: String? = null,
)

@Serializable
data class TlsObject(
    val serverName: String? = null,
    val allowInsecure: Boolean? = null,
    val alpn: List<String>? = null,
    val certificates: List<CertificateObject>? = null,
    val fingerprint: String? = null,
)

@Serializable
data class CertificateObject(
    val usage: String? = null,
    val keyFile: String? = null,
    val certificate: List<String>? = null,
    val key: List<String>? = null
)

@Serializable
data class FinalMaskObject(
    val tcp: List<MaskObject>? = null,
    val udp: List<MaskObject>? = null,
)

@Serializable
data class MaskObject(
    val type: String? = null,
    val settings: JsonObject? = null
)

@Serializable
data class SockoptObject(
    val mark: Int? = null,
    val tproxy: String? = null,
    val domainStrategy: String? = null,
    val acceptProxyProtocol: Boolean? = null,
)

// InboundObject.settings / OutboundObject.settings stay generic JsonObject because their shape
// depends on `protocol`. The VLESS classes below are the typed way to build one; flatten with
// toJsonObject() before assigning.

@Serializable
data class VlessClient(
    val id: String,
    val flow: String? = null,
    val level: Int? = null
)

// `decryption`/`encryption` have no default on purpose: XrayJson sets encodeDefaults=false and
// would drop them from the JSON, but xray-core wants the keys present even when the value is "none".
@Serializable
data class VlessInboundSettings(
    val clients: List<VlessClient>,
    val decryption: String
)

@Serializable
data class VlessVnextUser(
    val id: String,
    val encryption: String,
    val flow: String? = null,
    val level: Int? = null
)

@Serializable
data class VlessVnext(
    val address: String,
    val port: Int,
    val users: List<VlessVnextUser>
)

@Serializable
data class VlessOutboundSettings(
    val vnext: List<VlessVnext>
)

@Serializable
data class TrojanServer(
    val address: String,
    val port: Int,
    val password: String,
)

@Serializable
data class TrojanOutboundSettings(
    val servers: List<TrojanServer>,
)

fun VlessInboundSettings.toJsonObject(): JsonObject =
    XrayJson.default.encodeToJsonElement(VlessInboundSettings.serializer(), this).jsonObject

fun VlessOutboundSettings.toJsonObject(): JsonObject =
    XrayJson.default.encodeToJsonElement(VlessOutboundSettings.serializer(), this).jsonObject

fun TrojanOutboundSettings.toJsonObject(): JsonObject =
    XrayJson.default.encodeToJsonElement(TrojanOutboundSettings.serializer(), this).jsonObject
