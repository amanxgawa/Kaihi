package app.kaihi.android.feature.dashboard.impl.domain

import app.kaihi.android.core.mode.AppMode
import app.kaihi.android.core.mode.AppModeRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class AdvancedModeObserver @Inject constructor(
    private val appModeRepository: AppModeRepository,
) {
    val isAdvanced: Flow<Boolean> = appModeRepository.mode.map { it == AppMode.ADVANCED }
}
