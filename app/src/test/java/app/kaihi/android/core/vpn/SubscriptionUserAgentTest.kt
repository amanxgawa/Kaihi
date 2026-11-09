package app.kaihi.android.core.vpn

import app.kaihi.android.platform.mode.AppMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionUserAgentTest {

    @Test
    fun `validator accepts printable ASCII and rejects control or non ASCII characters`() {
        assertTrue(isValidSubscriptionUserAgent("PanelClient/1.2 (Android)"))
        assertTrue(isValidSubscriptionUserAgent("x".repeat(256)))
        assertFalse(isValidSubscriptionUserAgent(""))
        assertFalse(isValidSubscriptionUserAgent(" "))
        assertFalse(isValidSubscriptionUserAgent("bad\r\nInjected: yes"))
        assertFalse(isValidSubscriptionUserAgent("не ASCII"))
        assertFalse(isValidSubscriptionUserAgent("x".repeat(257)))
    }

    @Test
    fun `custom User-Agent is effective only in advanced mode`() {
        val custom = "PanelClient/1.2"

        assertEquals(
            "Kaihi/${app.kaihi.android.BuildConfig.VERSION_NAME}",
            effectiveSubscriptionUserAgent(custom, AppMode.NORMAL),
        )
        assertEquals(custom, effectiveSubscriptionUserAgent(custom, AppMode.ADVANCED))
        assertEquals(
            "Kaihi/${app.kaihi.android.BuildConfig.VERSION_NAME}",
            effectiveSubscriptionUserAgent(null, AppMode.ADVANCED),
        )
    }
}
