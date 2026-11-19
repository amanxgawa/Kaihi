package app.kaihi.android.core.vpn

/** Starts a session on [profile] with the user's current tunnel settings. */
interface XraySessionLauncher {
    suspend fun start(profile: XrayVpnProfile): Result<Unit>
}
