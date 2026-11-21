package app.kaihi.android.feature.dashboard.impl.domain

import app.kaihi.android.core.storage.InMemoryAppStorage
import app.kaihi.android.feature.dashboard.impl.data.StoredServerListPreferencesRepository
import app.kaihi.android.core.vpn.XraySubscription
import app.kaihi.android.core.vpn.XraySubscriptionStore
import app.kaihi.android.core.vpn.XrayVpnProfile
import app.kaihi.android.core.vpn.spec.XrayFingerprint
import app.kaihi.android.core.vpn.spec.XrayFingerprintSetting
import app.kaihi.android.core.vpn.spec.XraySecurity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerLibraryCoordinatorTest {
    @Test
    fun `recording a working fingerprint updates Auto lastWorking`() = runTest {
        val (coordinator, store) = coordinator(XrayFingerprintSetting.Auto())

        coordinator.recordWorkingFingerprint(SERVER_ID, XrayFingerprint.SAFARI)

        assertEquals(
            XrayFingerprintSetting.Auto(XrayFingerprint.SAFARI),
            store.profile().security.let { (it as XraySecurity.Reality).fingerprint },
        )
    }

    @Test
    fun `recording a working fingerprint leaves Fixed unchanged`() = runTest {
        val fixed = XrayFingerprintSetting.Fixed(XrayFingerprint.CHROME)
        val (coordinator, store) = coordinator(fixed)

        coordinator.recordWorkingFingerprint(SERVER_ID, XrayFingerprint.SAFARI)

        assertEquals(fixed, (store.profile().security as XraySecurity.Reality).fingerprint)
    }

    @Test
    fun `recording the current Auto fingerprint does not write the store`() = runTest {
        val auto = XrayFingerprintSetting.Auto(XrayFingerprint.SAFARI)
        val (coordinator, store) = coordinator(auto)

        coordinator.recordWorkingFingerprint(SERVER_ID, XrayFingerprint.SAFARI)

        assertEquals(auto, (store.profile().security as XraySecurity.Reality).fingerprint)
    }

    private suspend fun TestScope.coordinator(setting: XrayFingerprintSetting): Pair<ServerLibraryCoordinator, XraySubscriptionStore> {
        val profile = XrayVpnProfile(
            id = SERVER_ID,
            name = "test",
            serverAddress = "proxy.example",
            serverPort = 443,
            credential = "123e4567-e89b-12d3-a456-426614174000",
            security = XraySecurity.Reality("www.example.com", "public-key", "abcd", setting),
            addedAtMillis = 0L,
        )
        val storage = InMemoryAppStorage()
        val store = XraySubscriptionStore(storage, StandardTestDispatcher(testScheduler))
        store.update { listOf(XraySubscription("subscription", "Test", listOf(profile))) }
        return ServerLibraryCoordinator(store, StoredServerListPreferencesRepository(storage)) to store
    }

    private suspend fun XraySubscriptionStore.profile(): XrayVpnProfile =
        subscriptions.first().single().servers.single()

    private companion object {
        const val SERVER_ID = "server-1"
    }
}
