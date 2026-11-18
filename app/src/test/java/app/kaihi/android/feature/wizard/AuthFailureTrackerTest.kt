package app.kaihi.android.feature.wizard

import app.kaihi.android.feature.wizard.impl.domain.model.AuthFailureTracker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthFailureTrackerTest {
    @Test
    fun `three failures within ten minutes are likely blocked`() {
        var now = 0L
        val tracker = AuthFailureTracker { now }
        tracker.recordAuthFailure("server.example", 22)
        tracker.recordAuthFailure("server.example", 22)
        assertFalse(tracker.isLikelyBlocked("server.example", 22))

        tracker.recordAuthFailure("server.example", 22)
        assertTrue(tracker.isLikelyBlocked("server.example", 22))

        now += 10 * 60 * 1000L + 1
        assertFalse(tracker.isLikelyBlocked("server.example", 22))
    }

    @Test
    fun `failures on another host or port do not count`() {
        val tracker = AuthFailureTracker { 1_000L }
        repeat(3) { tracker.recordAuthFailure("other.example", 22) }
        repeat(3) { tracker.recordAuthFailure("server.example", 2222) }

        assertFalse(tracker.isLikelyBlocked("server.example", 22))
        assertFalse(tracker.isLikelyBlocked("other.example", 2222))
    }
}
