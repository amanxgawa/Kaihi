package app.kaihi.android.core.vpn.internal

import app.kaihi.android.core.vpn.FingerprintCandidate
import app.kaihi.android.core.vpn.spec.XrayFingerprint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber

internal class FingerprintSelector(
    private val startCore: suspend (rawJson: String) -> Unit,
    private val stopCore: suspend () -> Unit,
    private val probe: suspend () -> Boolean,
) {
    /** Returns the candidate whose core is left running; throws [NoWorkingFingerprintException]. */
    suspend fun select(candidates: List<FingerprintCandidate>): FingerprintCandidate {
        val tried = mutableListOf<XrayFingerprint>()
        try {
            for (candidate in candidates) {
                tried += candidate.fingerprint
                val passed = try {
                    startCore(candidate.rawJson)
                    probe()
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Throwable) {
                    false
                }

                Timber.i(
                    "FingerprintSelector: %s %s",
                    candidate.fingerprint.wireValue,
                    if (passed) "pass" else "fail",
                )
                if (passed) return candidate
                stopCore()
            }
        } catch (error: CancellationException) {
            try {
                withContext(NonCancellable) { stopCore() }
            } catch (cleanupError: Throwable) {
                if (cleanupError is CancellationException) throw cleanupError
            }
            throw error
        }

        throw NoWorkingFingerprintException(tried)
    }
}

internal class NoWorkingFingerprintException(tried: List<XrayFingerprint>) : Exception(
    "No TLS fingerprint reached the server (tried ${tried.joinToString(", ") { it.wireValue }})",
)
