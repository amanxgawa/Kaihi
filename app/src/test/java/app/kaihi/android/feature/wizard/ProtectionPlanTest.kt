package app.kaihi.android.feature.wizard

import app.kaihi.android.core.vpn.XrayProtectionState
import app.kaihi.android.core.vpn.XrayServerProtection
import app.kaihi.android.feature.wizard.impl.domain.model.PackageManager
import app.kaihi.android.feature.wizard.impl.domain.model.ProtectionPlan
import app.kaihi.android.feature.wizard.impl.domain.model.protectionPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ProtectionPlanTest {
    @Test
    fun `disabled protection skips every package manager`() {
        val expected = ProtectionPlan.Skip(
            XrayServerProtection(XrayProtectionState.OFF, XrayProtectionState.OFF),
        )

        PackageManager.entries.forEach { manager ->
            assertEquals(expected, protectionPlan(protectServer = false, packageManager = manager))
        }
    }

    @Test
    fun `only apt runs server protection`() {
        assertSame(ProtectionPlan.Run, protectionPlan(protectServer = true, packageManager = PackageManager.APT))

        val unsupported = XrayServerProtection(
            XrayProtectionState.UNSUPPORTED,
            XrayProtectionState.UNSUPPORTED,
        )
        listOf(PackageManager.DNF, PackageManager.YUM, PackageManager.PACMAN, PackageManager.APK).forEach { manager ->
            assertEquals(ProtectionPlan.Skip(unsupported), protectionPlan(protectServer = true, packageManager = manager))
        }
    }
}
