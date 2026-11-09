package app.kaihi.android.feature.settings.impl.ui.navigation

import androidx.compose.runtime.Composable
import app.kaihi.android.feature.settings.ExcludedAppsKey
import app.kaihi.android.feature.settings.impl.ui.screen.ExcludedAppsScreen
import app.kaihi.android.platform.navigation.Destination
import app.kaihi.android.platform.navigation.LocalAppNavigator
import javax.inject.Inject

class ExcludedAppsDestination @Inject constructor() : Destination<ExcludedAppsKey> {

    @Composable
    override fun Content(key: ExcludedAppsKey) {
        val navigator = LocalAppNavigator.current
        ExcludedAppsScreen(onBack = navigator::back)
    }
}
