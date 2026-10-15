package app.kaihi.android.core.vpn

interface XrayStatsSource {
    fun queryAllOutboundTrafficStats(): String?
}
