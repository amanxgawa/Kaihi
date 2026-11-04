package app.kaihi.android.core.vpn.configfactory

import app.kaihi.android.core.vpn.spec.FinalMaskObject
import app.kaihi.android.core.vpn.spec.MaskObject
import app.kaihi.android.core.vpn.spec.RawObject
import app.kaihi.android.core.vpn.spec.RealityObject
import app.kaihi.android.core.vpn.spec.StreamSettingsObject
import app.kaihi.android.core.vpn.spec.TlsObject
import app.kaihi.android.core.vpn.spec.XraySecurity
import app.kaihi.android.core.vpn.spec.XrayTransport
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

object XrayTransportFactory {
    const val DEFAULT_XHTTP_PATH = "/"

    fun streamSettings(
        transport: XrayTransport,
        security: XraySecurity,
        xmuxMaxConcurrency: Int,
        fragmentEnabled: Boolean,
    ): StreamSettingsObject {
        val (method, xhttpSettings, rawSettings) = when (transport) {
            is XrayTransport.Xhttp -> Triple(
                "xhttp",
                jsonObjectOf(
                    "path" to transport.path,
                    "mode" to transport.mode.wireValue,
                    "extra" to jsonObjectOf(
                        "xmux" to jsonObjectOf(
                            "maxConcurrency" to xmuxMaxConcurrency,
                            "hMaxRequestTimes" to XMUX_DEFAULT_MAX_REQUEST_TIMES,
                            "hMaxReusableSecs" to XMUX_DEFAULT_MAX_REUSABLE_SECS,
                        ),
                    ),
                ),
                null,
            )
            is XrayTransport.Raw -> Triple("raw", null, RawObject())
        }
        val (securityName, realitySettings, tlsSettings) = when (security) {
            is XraySecurity.Reality -> Triple(
                "reality",
                RealityObject(
                    serverName = security.serverName,
                    fingerprint = security.fingerprint.wireValue,
                    password = security.publicKey,
                    shortId = security.shortId,
                ),
                null,
            )
            is XraySecurity.Tls -> Triple(
                "tls",
                null,
                TlsObject(serverName = security.serverName, allowInsecure = security.allowInsecure),
            )
        }
        return StreamSettingsObject(
            method = method,
            security = securityName,
            xhttpSettings = xhttpSettings,
            rawSettings = rawSettings,
            realitySettings = realitySettings,
            tlsSettings = tlsSettings,
            finalmask = if (fragmentEnabled) {
                FinalMaskObject(
                    tcp = listOf(
                        MaskObject(
                            type = "fragment",
                            settings = jsonObjectOf(
                                "packets" to FRAGMENT_PACKETS,
                                "lengths" to JsonArray(listOf(JsonPrimitive(FRAGMENT_LENGTHS))),
                                "delays" to JsonArray(listOf(JsonPrimitive(FRAGMENT_DELAYS))),
                            ),
                        ),
                    ),
                )
            } else {
                null
            },
        )
    }

    fun serverXhttpRealityStreamSettings(reality: RealityObject): StreamSettingsObject =
        StreamSettingsObject(
            method = "xhttp",
            security = "reality",
            xhttpSettings = jsonObjectOf("path" to DEFAULT_XHTTP_PATH),
            realitySettings = reality,
        )

    // Xray's documented xmux default from the XHTTP guide (XTLS/Xray-core discussion #4113).
    private const val XMUX_DEFAULT_MAX_REQUEST_TIMES = "600-900"
    // Xray's documented xmux default from the XHTTP guide (XTLS/Xray-core discussion #4113).
    private const val XMUX_DEFAULT_MAX_REUSABLE_SECS = "1800-3000"
    // "tlshello" targets only the TLS ClientHello; the length and delay ranges are Kaihi's choice,
    // not Xray defaults.
    private const val FRAGMENT_PACKETS = "tlshello"
    private const val FRAGMENT_LENGTHS = "100-200"
    private const val FRAGMENT_DELAYS = "10-20"
}
