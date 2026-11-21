package app.kaihi.android.feature.wizard

import app.kaihi.android.feature.wizard.impl.ui.mvi.effectiveProtectServer
import app.kaihi.android.core.mode.AppMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectiveProtectServerTest {
    @Test
    fun `normal mode always protects while advanced mode follows the choice`() {
        assertTrue(effectiveProtectServer(AppMode.NORMAL, false))
        assertTrue(effectiveProtectServer(AppMode.NORMAL, true))
        assertFalse(effectiveProtectServer(AppMode.ADVANCED, false))
        assertTrue(effectiveProtectServer(AppMode.ADVANCED, true))
    }
}
