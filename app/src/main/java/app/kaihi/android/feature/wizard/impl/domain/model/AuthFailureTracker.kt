package app.kaihi.android.feature.wizard.impl.domain.model

internal class AuthFailureTracker(private val now: () -> Long = System::currentTimeMillis) {
    private val failures = mutableMapOf<Key, MutableList<Long>>()

    @Synchronized
    fun recordAuthFailure(host: String, port: Int) {
        val timestamp = now()
        val key = Key(host, port)
        val recent = failures.getOrPut(key, ::mutableListOf)
        recent.removeAll { timestamp - it > WINDOW_MS }
        recent += timestamp
        if (recent.size > MAX_FAILURES_PER_KEY) {
            recent.subList(0, recent.size - MAX_FAILURES_PER_KEY).clear()
        }
    }

    @Synchronized
    fun isLikelyBlocked(host: String, port: Int): Boolean {
        val timestamp = now()
        val key = Key(host, port)
        val recent = failures[key] ?: return false
        recent.removeAll { timestamp - it > WINDOW_MS }
        if (recent.isEmpty()) failures.remove(key)
        return recent.size >= FAILURE_THRESHOLD
    }

    private data class Key(val host: String, val port: Int)

    private companion object {
        const val WINDOW_MS = 10 * 60 * 1000L
        const val FAILURE_THRESHOLD = 3
        const val MAX_FAILURES_PER_KEY = 16
    }
}
