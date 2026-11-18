package app.kaihi.android.feature.wizard.impl.ui.mvi

import app.kaihi.android.R
import app.kaihi.android.feature.wizard.impl.domain.model.DeploymentEvent
import app.kaihi.android.feature.wizard.impl.domain.model.DeploymentStep
import app.kaihi.android.feature.wizard.impl.domain.model.XrayStartFailure
import app.kaihi.android.feature.wizard.impl.domain.model.XrayStartOutcome
import app.kaihi.android.feature.wizard.impl.domain.model.redactSecrets
import app.kaihi.android.platform.ui.util.UiText
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.toPersistentList

private const val MAX_LOG_LINES = 500

internal fun DeploymentUiState.reduce(event: DeploymentEvent): DeploymentUiState = when (event) {
    is DeploymentEvent.StepStarted -> withStepStatus(event.step, StepStatus.Running)
        .copy(phase = DeploymentPhase.InProgress)

    is DeploymentEvent.LogLine -> copy(
        logLines = logLines.mutate { lines ->
            lines.add(redactSecrets(event.line))
            if (lines.size > MAX_LOG_LINES) lines.removeAt(0)
        }
    )

    is DeploymentEvent.StepSucceeded -> withStepStatus(event.step, StepStatus.Success)

    is DeploymentEvent.StepFailed -> withStepStatus(event.step, StepStatus.Failed)
        .copy(phase = DeploymentPhase.Failed(event.error.toUiText(event.step)))

    is DeploymentEvent.Failed -> copy(phase = DeploymentPhase.Failed(UiText.Raw(event.error.message.orEmpty())))

    is DeploymentEvent.Completed -> copy(phase = DeploymentPhase.Succeeded(event.credentials))
}

private fun DeploymentUiState.withStepStatus(step: DeploymentStep, status: StepStatus): DeploymentUiState =
    copy(steps = steps.map { if (it.step == step) it.copy(status = status) else it }.toPersistentList())

private fun Throwable.toUiText(step: DeploymentStep): UiText = when (this) {
    is XrayStartFailure -> when (val startOutcome = outcome) {
        is XrayStartOutcome.PortBusy -> if (startOutcome.program != null) {
            UiText.Resource(R.string.wizard_error_port_busy_named, listOf(startOutcome.program))
        } else {
            UiText.Resource(R.string.wizard_error_port_busy)
        }
        XrayStartOutcome.Stopped -> UiText.Resource(R.string.wizard_error_xray_stopped)
        XrayStartOutcome.Running -> UiText.Raw(message ?: step.name)
    }
    else -> UiText.Raw(message ?: step.name)
}
