package app.kaihi.android.core.storage.di

import app.kaihi.android.core.storage.AppStorage
import app.kaihi.android.core.storage.internal.AppStorageImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class StorageModule {
    @Binds
    abstract fun bindAppStorage(impl: AppStorageImpl): AppStorage
}
