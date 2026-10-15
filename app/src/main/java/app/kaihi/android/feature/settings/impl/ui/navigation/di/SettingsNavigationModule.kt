package app.kaihi.android.feature.settings.impl.ui.navigation.di

import app.kaihi.android.feature.settings.SettingsKey
import app.kaihi.android.feature.settings.impl.ui.navigation.SettingsDestination
import app.kaihi.android.platform.navigation.Destination
import app.kaihi.android.platform.navigation.NavDestination
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsNavigationModule {

    @Binds
    @IntoMap
    @NavDestination(SettingsKey::class)
    abstract fun bindSettingsDestination(
        destination: SettingsDestination
    ): Destination<*>
}
