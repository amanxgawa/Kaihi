package app.kaihi.android.core.mode

import app.kaihi.android.core.mode.internal.StoredOnboardingState
import app.kaihi.android.core.storage.InMemoryAppStorage
import app.kaihi.android.core.storage.StorageDomain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoredOnboardingStateTest {
    @Test
    fun `fresh storage is not completed`() {
        assertFalse(StoredOnboardingState(InMemoryAppStorage()).isCompleted())
    }

    @Test
    fun `completion survives a fresh state over the same storage`() {
        val storage = InMemoryAppStorage()
        StoredOnboardingState(storage).markCompleted()

        assertTrue(StoredOnboardingState(storage).isCompleted())
    }

    @Test
    fun `completion is stored as true under the wizard completed key`() {
        val storage = InMemoryAppStorage()

        StoredOnboardingState(storage).markCompleted()

        assertEquals("true", storage.getString(StorageDomain.EXPERIENCE, "wizard_completed"))
        assertTrue(storage.getBoolean(StorageDomain.EXPERIENCE, "wizard_completed"))
    }
}
