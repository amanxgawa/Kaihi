package app.kaihi.android.feature.hello.impl.ui.navigation

import androidx.compose.runtime.Composable
import app.kaihi.android.feature.hello.HelloKey
import app.kaihi.android.feature.hello.impl.ui.screen.HelloScreen
import app.kaihi.android.feature.wizard.WizardKey
import app.kaihi.android.platform.navigation.Destination
import app.kaihi.android.platform.navigation.LocalAppNavigator
import javax.inject.Inject

class HelloDestination @Inject constructor() : Destination<HelloKey> {

    @Composable
    override fun Content(key: HelloKey) {
        val navigator = LocalAppNavigator.current

        HelloScreen(
            onSetUpServerClick = { navigator.navigate(WizardKey()) },
        )
    }
}
