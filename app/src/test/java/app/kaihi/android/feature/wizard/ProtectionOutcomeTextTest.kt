package app.kaihi.android.feature.wizard

import app.kaihi.android.R
import app.kaihi.android.core.vpn.XrayProtectionState
import app.kaihi.android.core.vpn.XrayServerProtection
import app.kaihi.android.feature.wizard.impl.ui.mvi.WizardContract
import app.kaihi.android.feature.wizard.impl.ui.mvi.protectionOutcomeText
import app.kaihi.android.core.mode.AppMode
import app.kaihi.android.platform.ui.util.UiText
import org.junit.Assert.assertEquals
import org.junit.Test

class ProtectionOutcomeTextTest {
    @Test
    fun `outcome mapping follows protection states and app mode`() {
        val enabled = XrayProtectionState.ENABLED
        val off = XrayProtectionState.OFF
        val unsupported = XrayProtectionState.UNSUPPORTED
        val failed = XrayProtectionState.FAILED
        val cases = listOf(
            Case(XrayServerProtection(enabled, enabled), R.string.wizard_protection_outcome_both_enabled, R.string.wizard_protection_outcome_both_enabled),
            Case(XrayServerProtection(enabled, off), R.string.wizard_protection_outcome_guard_enabled, R.string.wizard_protection_outcome_guard_enabled),
            Case(XrayServerProtection(enabled, failed), R.string.wizard_protection_outcome_guard_enabled, R.string.wizard_protection_outcome_guard_enabled),
            Case(XrayServerProtection(unsupported, unsupported), null, R.string.wizard_protection_outcome_unsupported),
            Case(XrayServerProtection(failed, off), null, R.string.wizard_protection_outcome_failed),
            Case(XrayServerProtection(off, failed), null, R.string.wizard_protection_outcome_failed),
            Case(XrayServerProtection(off, enabled), null, R.string.wizard_protection_outcome_failed),
            Case(XrayServerProtection(unsupported, enabled), null, R.string.wizard_protection_outcome_failed),
            Case(XrayServerProtection(off, off), null, null),
            Case(null, null, null),
        )

        cases.forEach { case ->
            assertEquals(case.normal, state(case.protection, AppMode.NORMAL).protectionOutcomeText().resourceId())
            assertEquals(case.advanced, state(case.protection, AppMode.ADVANCED).protectionOutcomeText().resourceId())
        }
    }

    private fun state(protection: XrayServerProtection?, mode: AppMode) =
        WizardContract.State(serverProtection = protection, appMode = mode)

    private fun UiText?.resourceId(): Int? = (this as? UiText.Resource)?.resId

    private data class Case(
        val protection: XrayServerProtection?,
        val normal: Int?,
        val advanced: Int?,
    )
}
