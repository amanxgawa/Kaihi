package app.kaihi.android.feature.dashboard.impl.ui.mvi

import app.kaihi.android.R
import app.kaihi.android.core.vpn.XrayFailure
import app.kaihi.android.platform.ui.util.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FailureMessageTest {
    @Test
    fun `maps no working fingerprint to its dashboard resource`() {
        assertEquals(
            UiText.Resource(R.string.dashboard_error_no_working_fingerprint),
            failureMessage(XrayFailure.NO_WORKING_FINGERPRINT),
        )
    }

    @Test
    fun `leaves other failures and no failure unmapped`() {
        assertNull(failureMessage(XrayFailure.OTHER))
        assertNull(failureMessage(null))
    }
}
