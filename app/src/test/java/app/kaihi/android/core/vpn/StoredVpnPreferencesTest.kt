package app.kaihi.android.core.vpn

import app.kaihi.android.BuildConfig
import app.kaihi.android.core.storage.ExperienceKeys
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
import org.junit.Assert.assertNull
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

    @Test
    fun `excluded packages round trip exact package names and never store Kaihi`() {
        val storage = InMemoryAppStorage()
        val preferences = StoredVpnPreferences(storage)
        val packages = setOf("org.example.bank_app", "com.example.bank.app", BuildConfig.APPLICATION_ID)

        preferences.setExcludedPackages(packages)

        val expected = setOf("org.example.bank_app", "com.example.bank.app")
        assertEquals(expected, preferences.excludedPackages.value)
        assertEquals(expected, StoredVpnPreferences(storage).excludedPackages.value)
        assertEquals(
            "com.example.bank.app,org.example.bank_app",
            storage.getString(StorageDomain.EXPERIENCE, "excluded_packages"),
        )
    }

    @Test
    fun `empty excluded packages round trip as empty`() {
        val storage = InMemoryAppStorage()
        val preferences = StoredVpnPreferences(storage)

        preferences.setExcludedPackages(setOf("com.example.bank.app"))
        preferences.setExcludedPackages(emptySet())

        assertEquals(emptySet<String>(), preferences.excludedPackages.value)
        assertEquals(emptySet<String>(), StoredVpnPreferences(storage).excludedPackages.value)
    }

    @Test
    fun `subscription User-Agent round trips and reset removes the stored value`() {
        val storage = InMemoryAppStorage()
        val preferences = StoredVpnPreferences(storage)

        assertTrue(preferences.setSubscriptionUserAgent("PanelClient/1.2"))
        assertEquals("PanelClient/1.2", preferences.subscriptionUserAgent.value)
        assertEquals("PanelClient/1.2", StoredVpnPreferences(storage).subscriptionUserAgent.value)

        assertTrue(preferences.setSubscriptionUserAgent(null))
        assertNull(preferences.subscriptionUserAgent.value)
        assertNull(storage.getString(StorageDomain.EXPERIENCE, ExperienceKeys.SUBSCRIPTION_USER_AGENT))
    }

    @Test
    fun `invalid subscription User-Agent is rejected without changing storage or state`() {
        val storage = InMemoryAppStorage()
        val preferences = StoredVpnPreferences(storage)
        preferences.setSubscriptionUserAgent("PanelClient/1.2")

        listOf("", " ", "x".repeat(257), "bad\r\nInjected: yes", "не ASCII").forEach { value ->
            assertFalse("$value should be rejected", preferences.setSubscriptionUserAgent(value))
            assertEquals("PanelClient/1.2", preferences.subscriptionUserAgent.value)
            assertEquals(
                "PanelClient/1.2",
                storage.getString(StorageDomain.EXPERIENCE, ExperienceKeys.SUBSCRIPTION_USER_AGENT),
            )
        }
    }
}
