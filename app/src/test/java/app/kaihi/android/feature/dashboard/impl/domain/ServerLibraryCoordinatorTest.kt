package app.kaihi.android.feature.dashboard.impl.domain

import app.kaihi.android.core.storage.AppStorage
import app.kaihi.android.core.storage.StorageDomain
import app.kaihi.android.core.vpn.XraySubscription
import app.kaihi.android.core.vpn.XraySubscriptionStore
import app.kaihi.android.core.vpn.XrayVpnProfile
import app.kaihi.android.core.vpn.spec.XrayFingerprint
import app.kaihi.android.core.vpn.spec.XrayFingerprintSetting
import app.kaihi.android.core.vpn.spec.XraySecurity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerLibraryCoordinatorTest {
    @Test
    fun `recording a working fingerprint updates Auto lastWorking`() = runTest {
        val (coordinator, store) = coordinator(XrayFingerprintSetting.Auto())

        coordinator.recordWorkingFingerprint(SERVER_ID, XrayFingerprint.SAFARI)

        assertEquals(1, store.updates)
        assertEquals(1, store.writes)
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

        assertEquals(0, store.updates)
        assertEquals(0, store.writes)
        assertEquals(fixed, (store.profile().security as XraySecurity.Reality).fingerprint)
    }

    @Test
    fun `recording the current Auto fingerprint does not write the store`() = runTest {
        val auto = XrayFingerprintSetting.Auto(XrayFingerprint.SAFARI)
        val (coordinator, store) = coordinator(auto)

        coordinator.recordWorkingFingerprint(SERVER_ID, XrayFingerprint.SAFARI)

        assertEquals(0, store.updates)
        assertEquals(0, store.writes)
        assertEquals(auto, (store.profile().security as XraySecurity.Reality).fingerprint)
    }

    private fun coordinator(setting: XrayFingerprintSetting): Pair<ServerLibraryCoordinator, MemoryStore> {
        val profile = XrayVpnProfile(
            id = SERVER_ID,
            name = "test",
            serverAddress = "proxy.example",
            serverPort = 443,
            uuid = "123e4567-e89b-12d3-a456-426614174000",
            security = XraySecurity.Reality("www.example.com", "public-key", "abcd", setting),
            addedAtMillis = 0L,
        )
        val store = MemoryStore(
            listOf(XraySubscription("subscription", "Test", listOf(profile))),
        )
        return ServerLibraryCoordinator(store, ServerListPreferencesRepository(NoopStorage)) to store
    }

    private fun MemoryStore.profile(): XrayVpnProfile =
        subscriptionsState.value.single().servers.single()

    private class MemoryStore(initial: List<XraySubscription>) : XraySubscriptionStore {
        val subscriptionsState = MutableStateFlow(initial)
        var updates = 0
            private set
        var writes = 0
            private set

        override val subscriptions: Flow<List<XraySubscription>> = subscriptionsState

        override suspend fun update(
            transform: (List<XraySubscription>) -> List<XraySubscription>,
        ): List<XraySubscription> {
            updates++
            val next = transform(subscriptionsState.value)
            if (next != subscriptionsState.value) {
                writes++
                subscriptionsState.value = next
            }
            return next
        }
    }

    private object NoopStorage : AppStorage {
        override fun getString(domain: StorageDomain, key: String): String? = null
        override fun putString(domain: StorageDomain, key: String, value: String) = Unit
        override fun getBoolean(domain: StorageDomain, key: String): Boolean = false
        override fun putBoolean(domain: StorageDomain, key: String, value: Boolean) = Unit
        override fun remove(domain: StorageDomain, key: String) = Unit
    }

    private companion object {
        const val SERVER_ID = "server-1"
    }
}
