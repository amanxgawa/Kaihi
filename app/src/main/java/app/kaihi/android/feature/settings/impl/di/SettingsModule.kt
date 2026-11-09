package app.kaihi.android.feature.settings.impl.di

import app.kaihi.android.feature.settings.impl.data.PackageManagerInstalledAppsRepository
import app.kaihi.android.feature.settings.impl.domain.InstalledAppsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SettingsModule {

    @Binds
    @Singleton
    abstract fun bindInstalledAppsRepository(
        impl: PackageManagerInstalledAppsRepository,
    ): InstalledAppsRepository
}
