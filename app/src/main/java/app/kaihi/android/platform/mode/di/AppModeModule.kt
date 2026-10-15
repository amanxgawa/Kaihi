package app.kaihi.android.platform.mode.di

import app.kaihi.android.platform.mode.AppModeRepository
import app.kaihi.android.platform.mode.internal.StoredAppModeRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AppModeModule {
    @Binds
    abstract fun bindAppModeRepository(impl: StoredAppModeRepository): AppModeRepository
}
