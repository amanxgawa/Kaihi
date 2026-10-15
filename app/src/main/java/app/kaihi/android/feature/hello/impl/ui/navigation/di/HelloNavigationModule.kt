package app.kaihi.android.feature.hello.impl.ui.navigation.di

import app.kaihi.android.feature.hello.AdvancedModeIntroKey
import app.kaihi.android.feature.hello.HelloKey
import app.kaihi.android.feature.hello.impl.ui.navigation.AdvancedModeIntroDestination
import app.kaihi.android.feature.hello.impl.ui.navigation.HelloDestination
import app.kaihi.android.platform.navigation.Destination
import app.kaihi.android.platform.navigation.NavDestination
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap

@Module
@InstallIn(SingletonComponent::class)
abstract class HelloNavigationModule {

    @Binds
    @IntoMap
    @NavDestination(HelloKey::class)
    abstract fun bindHelloDestination(
        destination: HelloDestination
    ): Destination<*>

    @Binds
    @IntoMap
    @NavDestination(AdvancedModeIntroKey::class)
    abstract fun bindAdvancedModeIntroDestination(
        destination: AdvancedModeIntroDestination
    ): Destination<*>
}
