package app.kaihi.android.feature.importing.impl.ui.navigation

import androidx.compose.runtime.Composable
import app.kaihi.android.feature.dashboard.DashboardKey
import app.kaihi.android.feature.importing.ImportKey
import app.kaihi.android.feature.importing.impl.ui.screen.ImportScreen
import app.kaihi.android.platform.navigation.Destination
import app.kaihi.android.platform.navigation.LocalAppNavigator
import javax.inject.Inject

class ImportDestination @Inject constructor() : Destination<ImportKey> {
    @Composable
    override fun Content(key: ImportKey) {
        val navigator = LocalAppNavigator.current
        ImportScreen(
            onBack = navigator::back,
            onNavigateToDashboard = { serverId -> navigator.replaceAll(DashboardKey(selectServerId = serverId)) },
        )
    }
}
