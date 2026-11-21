package app.kaihi.android.platform.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class SubscriptionUsageTest {
    @Test
    fun `traffic levels use normal warning and error thresholds`() {
        assertEquals(UsageLevel.NORMAL, usage(used = 89L, total = 100L).trafficLevel)
        assertEquals(UsageLevel.WARNING, usage(used = 90L, total = 100L).trafficLevel)
        assertEquals(UsageLevel.ERROR, usage(used = 100L, total = 100L).trafficLevel)
        assertEquals(UsageLevel.ERROR, usage(used = 150L, total = 100L).trafficLevel)
        assertEquals(UsageLevel.NORMAL, usage(used = 5L, total = 0L).trafficLevel)
        assertEquals(UsageLevel.NORMAL, usage(used = 5L, total = null).trafficLevel)
    }

    @Test
    fun `expiry levels use three day warning threshold`() {
        assertEquals(UsageLevel.NORMAL, subscriptionUsageUi(null, null, NOW + 3 * DAY + 1, null, NOW)?.expiryLevel)
        assertEquals(UsageLevel.WARNING, subscriptionUsageUi(null, null, NOW + 3 * DAY, null, NOW)?.expiryLevel)
        assertEquals(UsageLevel.ERROR, subscriptionUsageUi(null, null, NOW, null, NOW)?.expiryLevel)
        assertEquals(UsageLevel.NORMAL, subscriptionUsageUi(0L, null, null, null, NOW)?.expiryLevel)
    }

    @Test
    fun `blank announcement is removed`() {
        assertEquals(null, subscriptionUsageUi(0L, null, null, "  ", NOW)?.announce)
    }

    @Test
    fun `usage is absent without data and present after refresh failure`() {
        assertNull(subscriptionUsageUi(null, null, null, null, NOW))
        assertNotNull(subscriptionUsageUi(null, null, null, null, NOW, refreshFailed = true))
    }

    private fun usage(used: Long?, total: Long?) =
        subscriptionUsageUi(used, total, null, null, NOW)!!

    private companion object {
        const val NOW = 1_000_000_000_000L
        const val DAY = 86_400_000L
    }
}
