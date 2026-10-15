package app.kaihi.android.core.vpn.di

import android.os.SystemClock
import app.kaihi.android.core.vpn.XrayControl
import app.kaihi.android.core.vpn.XrayMonitoring
import app.kaihi.android.core.vpn.XrayServiceProvider
import app.kaihi.android.core.vpn.XrayStatsSource
import app.kaihi.android.core.vpn.XraySubscriptionStore
import app.kaihi.android.core.vpn.internal.ElapsedRealtimeSource
import app.kaihi.android.core.vpn.internal.RemoteXrayMonitoring
import app.kaihi.android.core.vpn.internal.StoredXraySubscriptionStore
import app.kaihi.android.core.vpn.internal.XrayProcessControl
import app.kaihi.android.core.vpn.internal.XrayRuntime
import app.kaihi.android.core.vpn.internal.XrayServiceProviderImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class VpnModule {
    @Binds
    abstract fun bindXrayControl(impl: XrayProcessControl): XrayControl

    @Binds
    abstract fun bindXrayMonitoring(impl: RemoteXrayMonitoring): XrayMonitoring

    @Binds
    abstract fun bindXrayStatsSource(impl: XrayRuntime): XrayStatsSource

    @Binds
    abstract fun bindXraySubscriptionStore(impl: StoredXraySubscriptionStore): XraySubscriptionStore

    @Binds
    abstract fun bindXrayServiceProvider(impl: XrayServiceProviderImpl): XrayServiceProvider

    companion object {
        @Provides
        fun provideElapsedRealtimeSource(): ElapsedRealtimeSource =
            ElapsedRealtimeSource(SystemClock::elapsedRealtime)
    }
}
