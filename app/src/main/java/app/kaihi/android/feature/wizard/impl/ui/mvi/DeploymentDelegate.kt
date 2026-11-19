package app.kaihi.android.feature.wizard.impl.ui.mvi

import androidx.annotation.StringRes
import app.kaihi.android.R
import app.kaihi.android.feature.wizard.impl.domain.WizardSetupCoordinator
import app.kaihi.android.feature.wizard.impl.domain.model.DeploymentEvent
import app.kaihi.android.core.mode.AppMode
import app.kaihi.android.platform.ui.mvi.ViewModelDelegate
import app.kaihi.android.platform.ui.util.UiText
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

internal class DeploymentDelegate @Inject constructor(
    private val coordinator: WizardSetupCoordinator,
) : ViewModelDelegate<WizardContract.State, WizardContract.Effect>() {

    private var job: Job? = null

    fun start() {
        val architecture = currentState.serverArchitecture ?: run {
            failBeforeStarting(R.string.wizard_error_architecture_unknown)
            return
        }
        job?.cancel()
        reduce { copy(deployment = DeploymentUiState()) }
        job = scope.launch {
            val sshConfig = currentState.toSshConfigOrNull()
            val events = if (sshConfig == null) {
                flowOf(DeploymentEvent.Failed(IllegalArgumentException("Complete the SSH connection details first.")))
            } else {
                coordinator.deploy(
                    sshConfig,
                    currentState.toSshAuth(),
                    architecture,
                    protectServer = effectiveProtectServer(currentState.appMode, currentState.protectServer),
                )
            }
            events.collect { event ->
                reduce { copy(deployment = deployment.reduce(event)) }
            }
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        reduce { copy(deployment = DeploymentUiState()) }
    }

    private fun failBeforeStarting(@StringRes reason: Int) {
        job?.cancel()
        job = null
        reduce {
            copy(deployment = DeploymentUiState(phase = DeploymentPhase.Failed(UiText.Resource(reason))))
        }
    }
}

internal fun effectiveProtectServer(appMode: AppMode, protectServer: Boolean): Boolean =
    appMode != AppMode.ADVANCED || protectServer
