package app.kaihi.android.core.vpn.internal

import android.content.Context
import android.net.VpnService
import androidx.core.content.ContextCompat
import app.kaihi.android.core.vpn.XrayControl
import app.kaihi.android.core.vpn.XrayRuntimeConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class XrayProcessControl @Inject constructor(
    @ApplicationContext private val context: Context,
) : XrayControl {

    override fun needsVpnPermission(): Boolean = VpnService.prepare(context) != null

    override suspend fun start(config: XrayRuntimeConfig): Result<Unit> = runCatching {
        require(!needsVpnPermission()) { "VPN permission must be granted before starting Xray." }
        ContextCompat.startForegroundService(
            context,
            KaihiVpnService.startIntent(context, config)
        )
    }

    override suspend fun reload(config: XrayRuntimeConfig): Result<Unit> = runCatching {
        require(!needsVpnPermission()) { "VPN permission must be granted before reloading Xray." }
        ContextCompat.startForegroundService(
            context,
            KaihiVpnService.reloadIntent(context, config)
        )
    }

    override suspend fun stop(): Result<Unit> = runCatching {
        context.startService(KaihiVpnService.stopIntent(context))
    }
}
