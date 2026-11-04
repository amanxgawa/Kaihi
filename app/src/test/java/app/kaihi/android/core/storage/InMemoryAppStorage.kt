package app.kaihi.android.core.storage

internal class InMemoryAppStorage : AppStorage {
    private val values = mutableMapOf<Pair<StorageDomain, String>, String>()

    override fun getString(domain: StorageDomain, key: String): String? = values[domain to key]

    override fun putString(domain: StorageDomain, key: String, value: String) {
        values[domain to key] = value
    }

    override fun getBoolean(domain: StorageDomain, key: String): Boolean =
        getString(domain, key) == "true"

    override fun putBoolean(domain: StorageDomain, key: String, value: Boolean) {
        putString(domain, key, value.toString())
    }

    override fun remove(domain: StorageDomain, key: String) {
        values.remove(domain to key)
    }
}
