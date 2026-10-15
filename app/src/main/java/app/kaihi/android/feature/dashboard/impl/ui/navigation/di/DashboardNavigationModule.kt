package app.kaihi.android.feature.dashboard.impl.ui.navigation.di

import app.kaihi.android.feature.dashboard.DashboardKey
import app.kaihi.android.feature.dashboard.XrayLogsKey
import app.kaihi.android.feature.dashboard.impl.ui.navigation.DashboardDestination
import app.kaihi.android.feature.dashboard.impl.ui.navigation.XrayLogsDestination
import app.kaihi.android.platform.navigation.Destination
import app.kaihi.android.platform.navigation.NavDestination
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap

@Module
@InstallIn(SingletonComponent::class)
abstract class DashboardNavigationModule {

    @Binds
    @IntoMap
    @NavDestination(DashboardKey::class)
    abstract fun bindDashboardDestination(
        destination: DashboardDestination
    ): Destination<*>

    @Binds
    @IntoMap
    @NavDestination(XrayLogsKey::class)
    abstract fun bindXrayLogsDestination(
        destination: XrayLogsDestination
    ): Destination<*>
}
