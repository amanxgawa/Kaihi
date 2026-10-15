package app.kaihi.android.feature.dashboard.impl.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import app.kaihi.android.feature.dashboard.DashboardKey
import app.kaihi.android.feature.dashboard.impl.ui.screen.DashboardScreen
import app.kaihi.android.feature.settings.SettingsKey
import app.kaihi.android.feature.wizard.WizardKey
import app.kaihi.android.platform.navigation.Destination
import app.kaihi.android.platform.navigation.LocalAppNavigator
import javax.inject.Inject

class DashboardDestination @Inject constructor() : Destination<DashboardKey> {

    @Composable
    override fun Content(key: DashboardKey) {
        val navigator = LocalAppNavigator.current

        // The key is saved with the back stack and handed over again when a killed process is restored, so
        // selectServerId applies only on this entry's first composition. A new entry starts without the flag.
        var isSelectionHandedOver by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(Unit) { isSelectionHandedOver = true }

        DashboardScreen(
            selectServerId = key.selectServerId.takeUnless { isSelectionHandedOver },
            onSettingsClick = { navigator.navigate(SettingsKey) },
            onNavigateToWizard = { subscriptionId -> navigator.navigate(WizardKey(subscriptionId)) },
        )
    }
}
