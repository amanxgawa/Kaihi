package app.kaihi.android.feature.hello.impl.domain

import app.kaihi.android.core.mode.AppMode
import app.kaihi.android.core.mode.AppModeRepository
import javax.inject.Inject

internal class AdvancedModeSwitch @Inject constructor(
    private val appModeRepository: AppModeRepository,
) {
    fun enable() = appModeRepository.setMode(AppMode.ADVANCED)
}
