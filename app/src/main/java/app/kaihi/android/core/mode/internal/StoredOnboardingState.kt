package app.kaihi.android.core.mode.internal

import app.kaihi.android.core.mode.OnboardingState
import app.kaihi.android.core.storage.AppStorage
import app.kaihi.android.core.storage.ExperienceKeys
import app.kaihi.android.core.storage.StorageDomain
import javax.inject.Inject

internal class StoredOnboardingState @Inject constructor(
    private val appStorage: AppStorage,
) : OnboardingState {
    override fun isCompleted(): Boolean =
        appStorage.getBoolean(StorageDomain.EXPERIENCE, ExperienceKeys.WIZARD_COMPLETED)

    override fun markCompleted() {
        appStorage.putBoolean(StorageDomain.EXPERIENCE, ExperienceKeys.WIZARD_COMPLETED, true)
    }
}
