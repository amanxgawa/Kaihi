package app.kaihi.android.feature.hello.impl.ui.navigation

import androidx.compose.runtime.Composable
import app.kaihi.android.feature.hello.AdvancedModeIntroKey
import app.kaihi.android.feature.hello.impl.ui.screen.AdvancedModeIntroScreen
import app.kaihi.android.platform.navigation.Destination
import app.kaihi.android.platform.navigation.LocalAppNavigator
import javax.inject.Inject

class AdvancedModeIntroDestination @Inject constructor() : Destination<AdvancedModeIntroKey> {

    @Composable
    override fun Content(key: AdvancedModeIntroKey) {
        val navigator = LocalAppNavigator.current

        AdvancedModeIntroScreen(onBackClick = navigator::back)
    }
}
