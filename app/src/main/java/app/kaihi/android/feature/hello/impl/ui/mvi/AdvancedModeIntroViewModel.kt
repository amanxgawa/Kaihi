package app.kaihi.android.feature.hello.impl.ui.mvi

import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import app.kaihi.android.feature.hello.impl.domain.AdvancedModeSwitch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@Stable
@HiltViewModel
internal class AdvancedModeIntroViewModel @Inject constructor(
    private val advancedModeSwitch: AdvancedModeSwitch,
) : ViewModel() {

    fun confirmAdvancedMode() {
        advancedModeSwitch.enable()
    }
}
