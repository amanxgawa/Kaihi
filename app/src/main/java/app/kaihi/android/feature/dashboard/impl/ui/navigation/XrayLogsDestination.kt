package app.kaihi.android.feature.dashboard.impl.ui.navigation

import androidx.compose.runtime.Composable
import app.kaihi.android.feature.dashboard.XrayLogsKey
import app.kaihi.android.feature.dashboard.impl.ui.screen.XrayLogsScreen
import app.kaihi.android.platform.navigation.Destination
import app.kaihi.android.platform.navigation.LocalAppNavigator
import javax.inject.Inject

class XrayLogsDestination @Inject constructor() : Destination<XrayLogsKey> {

    @Composable
    override fun Content(key: XrayLogsKey) {
        val navigator = LocalAppNavigator.current

        XrayLogsScreen(onBackClick = navigator::back)
    }
}
