package app.kaihi.android.core.vpn.internal

import app.kaihi.android.core.storage.AppStorage
import app.kaihi.android.core.storage.SecureStorageUnreadableException
import app.kaihi.android.core.storage.StorageDomain
import app.kaihi.android.core.vpn.XrayJson
import app.kaihi.android.core.vpn.XraySubscription
import app.kaihi.android.core.vpn.XraySubscriptionStore
import app.kaihi.android.platform.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import timber.log.Timber

@Singleton
internal class StoredXraySubscriptionStore @Inject constructor(
    private val appStorage: AppStorage,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : XraySubscriptionStore {

    private val mutex = Mutex()

    // null until the first read, so a collector never observes an empty list that was never stored.
    private val cached = MutableStateFlow<List<XraySubscription>?>(null)

    override val subscriptions: Flow<List<XraySubscription>> = flow {
        if (cached.value == null) mutex.withLock { loadIfNeeded() }
        emitAll(cached.filterNotNull())
    }

    override suspend fun update(
        transform: (List<XraySubscription>) -> List<XraySubscription>,
    ): List<XraySubscription> = mutex.withLock {
        val current = loadIfNeeded()
        val next = transform(current)
        if (next != current) {
            withContext(ioDispatcher) { persist(next) }
            cached.value = next
        }
        next
    }

    /** Callers hold [mutex]. */
    private suspend fun loadIfNeeded(): List<XraySubscription> =
        cached.value ?: withContext(ioDispatcher) { load() }.also { cached.value = it }

    private fun load(): List<XraySubscription> {
        // Cleared, not kept: it is encrypted under a Keystore key that no longer exists, so nothing
        // can read it again and the next update() would overwrite it silently anyway. An empty server
        // list is visible to the user; a vanished SSH host key pin is not, which is why that one stays.
        val payload = try {
            appStorage.getString(StorageDomain.XRAY_PROFILES, KEY_SUBSCRIPTIONS)
        } catch (error: SecureStorageUnreadableException) {
            Timber.e(error, "Stored subscriptions can no longer be decrypted - discarding them")
            appStorage.remove(StorageDomain.XRAY_PROFILES, KEY_SUBSCRIPTIONS)
            return emptyList()
        } ?: return emptyList()

        return runCatching {
            XrayJson.default.decodeFromString(SUBSCRIPTIONS_SERIALIZER, payload)
        }
            .getOrElse { error ->
                Timber.e(error, "Stored subscriptions are undecodable")
                emptyList()
            }
    }

    private fun persist(subscriptions: List<XraySubscription>) {
        appStorage.putString(
            StorageDomain.XRAY_PROFILES,
            KEY_SUBSCRIPTIONS,
            XrayJson.default.encodeToString(SUBSCRIPTIONS_SERIALIZER, subscriptions),
        )
    }

    private companion object {
        const val KEY_SUBSCRIPTIONS = "subscriptions"
        val SUBSCRIPTIONS_SERIALIZER = ListSerializer(XraySubscription.serializer())
    }
}
