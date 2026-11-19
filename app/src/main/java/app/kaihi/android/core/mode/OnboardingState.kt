package app.kaihi.android.core.mode

import app.kaihi.android.core.storage.AppStorage
import app.kaihi.android.core.storage.ExperienceKeys
import app.kaihi.android.core.storage.StorageDomain
import javax.inject.Inject

/** Whether the user has finished first-run setup, by the wizard or by importing a server. Reads touch disk. */
class OnboardingState @Inject constructor(
    private val appStorage: AppStorage,
) {
    fun isCompleted(): Boolean =
        appStorage.getBoolean(StorageDomain.EXPERIENCE, ExperienceKeys.WIZARD_COMPLETED)

    fun markCompleted() {
        appStorage.putBoolean(StorageDomain.EXPERIENCE, ExperienceKeys.WIZARD_COMPLETED, true)
    }
}
