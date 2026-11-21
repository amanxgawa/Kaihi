package app.kaihi.android.feature.dashboard.impl.di

import app.kaihi.android.feature.dashboard.impl.data.StoredServerListPreferencesRepository
import app.kaihi.android.feature.dashboard.impl.domain.ServerListPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DashboardModule {

    @Binds
    @Singleton
    abstract fun bindServerListPreferencesRepository(
        impl: StoredServerListPreferencesRepository,
    ): ServerListPreferencesRepository
}
