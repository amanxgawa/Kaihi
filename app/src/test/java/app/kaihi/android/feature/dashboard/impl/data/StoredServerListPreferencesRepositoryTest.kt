package app.kaihi.android.feature.dashboard.impl.data

import app.kaihi.android.core.storage.AppStorage
import app.kaihi.android.core.storage.StorageDomain
import app.kaihi.android.feature.dashboard.impl.domain.model.ServerListPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class StoredServerListPreferencesRepositoryTest {

    @Test
    fun `writes server list preferences with their persisted keys`() {
        val storage = FakeAppStorage()
        val repository = StoredServerListPreferencesRepository(storage)

        repository.select("abc")
        repository.setCollapsed("s1", true)

        assertEquals("abc", storage.getString(StorageDomain.EXPERIENCE, "selected_server_id"))
        assertEquals("s1", storage.getString(StorageDomain.EXPERIENCE, "collapsed_subscription_ids"))
    }

    @Test
    fun `reads preferences previously stored under their persisted keys`() {
        val storage = FakeAppStorage().apply {
            putString(StorageDomain.EXPERIENCE, "selected_server_id", "x")
            putString(StorageDomain.EXPERIENCE, "collapsed_subscription_ids", "a,b")
        }

        val repository = StoredServerListPreferencesRepository(storage)

        assertEquals(ServerListPreferences("x", setOf("a", "b")), repository.preferences.value)
    }

    private class FakeAppStorage : AppStorage {
        private val strings = mutableMapOf<Pair<StorageDomain, String>, String>()

        override fun getString(domain: StorageDomain, key: String): String? = strings[domain to key]

        override fun putString(domain: StorageDomain, key: String, value: String) {
            strings[domain to key] = value
        }

        override fun getBoolean(domain: StorageDomain, key: String): Boolean = false

        override fun putBoolean(domain: StorageDomain, key: String, value: Boolean) = Unit

        override fun remove(domain: StorageDomain, key: String) {
            strings.remove(domain to key)
        }
    }
}
