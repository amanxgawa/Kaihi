package app.kaihi.android.core.mode.di

import app.kaihi.android.core.mode.AppModeRepository
import app.kaihi.android.core.mode.OnboardingState
import app.kaihi.android.core.mode.internal.StoredAppModeRepository
import app.kaihi.android.core.mode.internal.StoredOnboardingState
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AppModeModule {
    @Binds
    abstract fun bindAppModeRepository(impl: StoredAppModeRepository): AppModeRepository

    @Binds
    abstract fun bindOnboardingState(impl: StoredOnboardingState): OnboardingState
}
