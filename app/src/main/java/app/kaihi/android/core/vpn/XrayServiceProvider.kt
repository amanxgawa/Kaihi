package app.kaihi.android.core.vpn

interface XrayServiceProvider {
    val control: XrayControl
    val monitoring: XrayMonitoring
}
