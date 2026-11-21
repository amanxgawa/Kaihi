package app.kaihi.android.feature.importing.impl.ui.navigation

import androidx.compose.runtime.Composable
import app.kaihi.android.feature.dashboard.DashboardKey
import app.kaihi.android.feature.importing.ImportKey
import app.kaihi.android.feature.importing.impl.ui.screen.ImportSheet
import app.kaihi.android.feature.wizard.WizardKey
import app.kaihi.android.platform.navigation.BottomSheetSceneStrategy
import app.kaihi.android.platform.navigation.Destination
import app.kaihi.android.platform.navigation.LocalAppNavigator
import javax.inject.Inject

class ImportDestination @Inject constructor() : Destination<ImportKey> {
    override val metadata = BottomSheetSceneStrategy.bottomSheet()

    @Composable
    override fun Content(key: ImportKey) {
        val navigator = LocalAppNavigator.current
        ImportSheet(
            initialText = key.text,
            offerServerSetup = key.offerServerSetup,
            onClose = navigator::back,
            onSetUpServer = {
                navigator.back()
                navigator.navigate(WizardKey())
            },
            onNavigateToDashboard = { serverId -> navigator.replaceAll(DashboardKey(selectServerId = serverId)) },
            onShowExisting = { serverId -> navigator.replaceAll(DashboardKey(selectServerId = serverId)) },
        )
    }
}
