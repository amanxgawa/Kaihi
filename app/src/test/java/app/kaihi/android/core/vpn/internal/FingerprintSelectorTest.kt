package app.kaihi.android.core.vpn.internal

import app.kaihi.android.core.vpn.FingerprintCandidate
import app.kaihi.android.core.vpn.spec.XrayFingerprint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FingerprintSelectorTest {
    private val candidates = listOf(
        XrayFingerprint.FIREFOX,
        XrayFingerprint.CHROME,
        XrayFingerprint.SAFARI,
    ).mapIndexed { index, fingerprint ->
        FingerprintCandidate(fingerprint, "config-$index")
    }

    @Test
    fun `first candidate passing leaves its core running`() = runTest {
        val starts = mutableListOf<String>()
        var stops = 0
        val chosen = FingerprintSelector(
            startCore = { starts += it },
            stopCore = { stops++ },
            probe = { true },
        ).select(candidates)

        assertSame(candidates.first(), chosen)
        assertEquals(listOf("config-0"), starts)
        assertEquals(0, stops)
    }

    @Test
    fun `failed candidates are stopped before the next core starts`() = runTest {
        val calls = mutableListOf<String>()
        var probeCount = 0
        val chosen = FingerprintSelector(
            startCore = { calls += "start:$it" },
            stopCore = { calls += "stop" },
            probe = { ++probeCount == 3 },
        ).select(candidates)

        assertSame(candidates[2], chosen)
        assertEquals(
            listOf("start:config-0", "stop", "start:config-1", "stop", "start:config-2"),
            calls,
        )
    }

    @Test
    fun `all failures stop each started core and list only tried fingerprints`() = runTest {
        var starts = 0
        var stops = 0
        val error = runCatching {
            FingerprintSelector(
                startCore = { starts++ },
                stopCore = { stops++ },
                probe = { false },
            ).select(candidates)
        }.exceptionOrNull()

        assertTrue(error is NoWorkingFingerprintException)
        assertEquals(
            "No TLS fingerprint reached the server (tried firefox, chrome, safari)",
            error?.message,
        )
        assertEquals(candidates.size, starts)
        assertEquals(starts, stops)
        assertFalse(error!!.message!!.contains("config-"))
    }

    @Test
    fun `a core start failure is stopped and the next candidate is tried`() = runTest {
        val calls = mutableListOf<String>()
        val chosen = FingerprintSelector(
            startCore = {
                calls += "start:$it"
                if (it == "config-0") error("private configuration failure")
            },
            stopCore = { calls += "stop" },
            probe = { true },
        ).select(candidates)

        assertSame(candidates[1], chosen)
        assertEquals(listOf("start:config-0", "stop", "start:config-1"), calls)
    }

    @Test
    fun `cancellation stops the running core and propagates`() = runTest {
        var stops = 0
        val error = runCatching {
            FingerprintSelector(
                startCore = {},
                stopCore = { stops++ },
                probe = { throw CancellationException("cancel probe") },
            ).select(candidates)
        }.exceptionOrNull()

        assertTrue(error is CancellationException)
        assertEquals(1, stops)
    }
}
