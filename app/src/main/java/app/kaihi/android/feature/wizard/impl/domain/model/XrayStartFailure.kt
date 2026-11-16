package app.kaihi.android.feature.wizard.impl.domain.model

internal class XrayStartFailure(val outcome: XrayStartOutcome) : Exception(
    when (outcome) {
        is XrayStartOutcome.PortBusy -> "Port 443 is already in use on the server"
        else -> "Xray stopped right after starting"
    },
)
