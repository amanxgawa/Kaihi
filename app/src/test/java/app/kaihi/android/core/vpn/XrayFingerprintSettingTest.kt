package app.kaihi.android.core.vpn

import app.kaihi.android.core.vpn.spec.XrayFingerprint
import app.kaihi.android.core.vpn.spec.XrayFingerprintSetting
import app.kaihi.android.core.vpn.spec.candidates
import app.kaihi.android.core.vpn.spec.preferred
import org.junit.Assert.assertEquals
import org.junit.Test

class XrayFingerprintSettingTest {
    @Test
    fun `auto uses supported candidate order`() {
        assertEquals(
            listOf(
                XrayFingerprint.FIREFOX,
                XrayFingerprint.SAFARI,
                XrayFingerprint.CHROME,
                XrayFingerprint.EDGE,
                XrayFingerprint.IOS,
                XrayFingerprint.ANDROID,
            ),
            XrayFingerprintSetting.Auto().candidates(),
        )
    }

    @Test
    fun `auto puts last working first without duplicates`() {
        val candidates = XrayFingerprintSetting.Auto(XrayFingerprint.CHROME).candidates()

        assertEquals(XrayFingerprint.CHROME, candidates.first())
        assertEquals(6, candidates.size)
        assertEquals(6, candidates.toSet().size)
    }

    @Test
    fun `fixed only includes its fingerprint`() {
        assertEquals(
            listOf(XrayFingerprint.SAFARI),
            XrayFingerprintSetting.Fixed(XrayFingerprint.SAFARI).candidates(),
        )
        assertEquals(XrayFingerprint.FIREFOX, XrayFingerprintSetting.Auto().preferred())
    }
}
