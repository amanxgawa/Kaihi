package app.kaihi.android.feature.wizard.impl.domain.model

internal sealed interface XrayStartOutcome {
    data object Running : XrayStartOutcome
    data class PortBusy(val program: String?) : XrayStartOutcome
    data object Stopped : XrayStartOutcome
}
