package app.kaihi.android.core.vpn

/** Saved tunnel inputs. Config factories resolve advanced values according to [advancedMode]. */
data class TunnelSettings(
    val advancedMode: Boolean,
    val ipv6Enabled: Boolean,
    val excludedPackages: Set<String> = emptySet(),
)
