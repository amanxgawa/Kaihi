package app.kaihi.android.feature.importing.impl.ui.navigation.di

import app.kaihi.android.feature.importing.ImportKey
import app.kaihi.android.feature.importing.impl.ui.navigation.ImportDestination
import app.kaihi.android.platform.navigation.Destination
import app.kaihi.android.platform.navigation.NavDestination
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap

@Module
@InstallIn(SingletonComponent::class)
abstract class ImportNavigationModule {
    @Binds
    @IntoMap
    @NavDestination(ImportKey::class)
    abstract fun bindImportDestination(destination: ImportDestination): Destination<*>
}
