package app.kaihi.android.core.vpn

import app.kaihi.android.core.storage.InMemoryAppStorage
import app.kaihi.android.core.storage.StorageDomain
import app.kaihi.android.core.vpn.internal.StoredVpnPreferences
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoredVpnPreferencesTest {

    @Test
    fun `IPv6 defaults to disabled when nothing is persisted`() {
        val preferences = StoredVpnPreferences(InMemoryAppStorage())

        assertFalse(preferences.ipv6Enabled.value)
    }

    @Test
    fun `enabling IPv6 emits the choice and survives a fresh instance`() = runTest {
        val storage = InMemoryAppStorage()
        val preferences = StoredVpnPreferences(storage)
        val emissions = async(start = CoroutineStart.UNDISPATCHED) {
            preferences.ipv6Enabled.take(2).toList()
        }

        preferences.setIpv6Enabled(true)

        assertTrue(preferences.ipv6Enabled.value)
        assertEquals(listOf(false, true), emissions.await())
        assertTrue(storage.getBoolean(StorageDomain.EXPERIENCE, "ipv6_enabled"))
        assertTrue(StoredVpnPreferences(storage).ipv6Enabled.value)
    }

    @Test
    fun `disabling IPv6 again survives a fresh instance`() {
        val storage = InMemoryAppStorage()
        val preferences = StoredVpnPreferences(storage)

        preferences.setIpv6Enabled(true)
        preferences.setIpv6Enabled(false)

        assertFalse(preferences.ipv6Enabled.value)
        assertFalse(StoredVpnPreferences(storage).ipv6Enabled.value)
    }
}
