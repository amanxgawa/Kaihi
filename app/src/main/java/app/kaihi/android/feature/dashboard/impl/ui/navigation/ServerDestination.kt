package app.kaihi.android.feature.dashboard.impl.ui.navigation

import androidx.compose.runtime.Composable
import app.kaihi.android.feature.dashboard.ServerKey
import app.kaihi.android.feature.dashboard.impl.ui.screen.ServerScreen
import app.kaihi.android.platform.navigation.Destination
import app.kaihi.android.platform.navigation.LocalAppNavigator
import javax.inject.Inject

class ServerDestination @Inject constructor() : Destination<ServerKey> {

    @Composable
    override fun Content(key: ServerKey) {
        val navigator = LocalAppNavigator.current
        ServerScreen(
            serverId = key.serverId,
            onBack = navigator::back,
        )
    }
}
