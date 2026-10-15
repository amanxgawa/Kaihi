package app.kaihi.android.feature.wizard.impl.ui.navigation

import androidx.compose.runtime.Composable
import app.kaihi.android.feature.dashboard.DashboardKey
import app.kaihi.android.feature.wizard.WizardKey
import app.kaihi.android.feature.wizard.impl.ui.screen.WizardScreen
import app.kaihi.android.platform.navigation.Destination
import app.kaihi.android.platform.navigation.LocalAppNavigator
import javax.inject.Inject

class WizardDestination @Inject constructor() : Destination<WizardKey> {

    @Composable
    override fun Content(key: WizardKey) {
        val navigator = LocalAppNavigator.current

        WizardScreen(
            targetSubscriptionId = key.subscriptionId,
            onNavigateBack = navigator::back,
            onNavigateToDashboard = { serverId -> navigator.replaceAll(DashboardKey(selectServerId = serverId)) },
        )
    }
}
