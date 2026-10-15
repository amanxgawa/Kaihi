package app.kaihi.android.feature.wizard.impl.ui.navigation.di

import app.kaihi.android.feature.wizard.WizardKey
import app.kaihi.android.feature.wizard.impl.ui.navigation.WizardDestination
import app.kaihi.android.platform.navigation.Destination
import app.kaihi.android.platform.navigation.NavDestination
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap

@Module
@InstallIn(SingletonComponent::class)
abstract class WizardNavigationModule {

    @Binds
    @IntoMap
    @NavDestination(WizardKey::class)
    abstract fun bindWizardDestination(
        destination: WizardDestination
    ): Destination<*>
}
