package app.kaihi.android.core.mode

import app.kaihi.android.core.storage.InMemoryAppStorage
import app.kaihi.android.core.storage.StorageDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStateTest {
    @Test
    fun `fresh storage is not completed`() {
        assertFalse(OnboardingState(InMemoryAppStorage()).isCompleted())
    }

    @Test
    fun `completion survives a fresh state over the same storage`() {
        val storage = InMemoryAppStorage()
        OnboardingState(storage).markCompleted()

        assertTrue(OnboardingState(storage).isCompleted())
    }

    @Test
    fun `completion is stored as true under the wizard completed key`() {
        val storage = InMemoryAppStorage()

        OnboardingState(storage).markCompleted()

        assertEquals("true", storage.getString(StorageDomain.EXPERIENCE, "wizard_completed"))
        assertTrue(storage.getBoolean(StorageDomain.EXPERIENCE, "wizard_completed"))
    }
}
