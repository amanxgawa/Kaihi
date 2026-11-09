package app.kaihi.android.feature.wizard.impl.ui.mvi

import app.kaihi.android.core.security.Secret
import app.kaihi.android.core.ssh.model.SshAuth
import app.kaihi.android.core.ssh.model.SshConfig
import app.kaihi.android.core.vpn.XrayProtectionState
import app.kaihi.android.core.vpn.XrayServerProtection
import app.kaihi.android.R
import app.kaihi.android.feature.wizard.impl.domain.model.WizardProfileDraft
import app.kaihi.android.platform.mode.AppMode
import app.kaihi.android.platform.ui.util.UiSecret
import app.kaihi.android.platform.ui.util.UiText

internal fun WizardContract.State.toSshConfigOrNull(): SshConfig? =
    sshPort.toIntOrNull()?.takeIf { it in 1..65_535 }?.let { port ->
        serverAddress.trim().takeIf(String::isNotEmpty)?.let { host ->
            username.trim().takeIf(String::isNotEmpty)?.let { user ->
                SshConfig(host = host, port = port, username = user, expectedFingerprint = sshFingerprint.trim().ifBlank { null })
            }
        }
    }

private fun UiSecret.toSecret(): Secret = Secret(value)

internal fun WizardContract.State.toSshAuth(): SshAuth = when (serverAuthMethod) {
    WizardContract.ServerAuthMethod.PASSWORD -> SshAuth.Password(password.toSecret())
    WizardContract.ServerAuthMethod.SSH_KEY -> SshAuth.PrivateKey(sshKey.toSecret())
}

internal fun WizardContract.State.toProfileDraft(): WizardProfileDraft = WizardProfileDraft(
    serverAddress = serverAddress.trim(),
    sshPort = sshPort.toInt(),
    sshUsername = username.trim(),
    sshExpectedFingerprint = sshFingerprint.trim().ifBlank { null },
    uuid = uuid.trim(),
    realityServerName = realityServerName.trim(),
    realityPublicKey = realityPublicKey.trim(),
    realityShortId = realityShortId.trim(),
    countryCode = serverCountryCode,
    serverProtection = serverProtection,
)

internal fun WizardContract.State.protectionOutcomeText(): UiText? {
    val guard = serverProtection?.bruteForceGuard
    val updates = serverProtection?.securityUpdates
    val resource = when {
        guard == XrayProtectionState.ENABLED && updates == XrayProtectionState.ENABLED ->
            R.string.wizard_protection_outcome_both_enabled

        guard == XrayProtectionState.ENABLED -> R.string.wizard_protection_outcome_guard_enabled
        guard == XrayProtectionState.UNSUPPORTED && updates == XrayProtectionState.UNSUPPORTED ->
            if (appMode == AppMode.ADVANCED) R.string.wizard_protection_outcome_unsupported else null

        guard == XrayProtectionState.FAILED || updates == XrayProtectionState.FAILED ||
            (updates == XrayProtectionState.ENABLED && guard != XrayProtectionState.ENABLED) ->
            if (appMode == AppMode.ADVANCED) R.string.wizard_protection_outcome_failed else null

        else -> null
    }
    return resource?.let(UiText::Resource)
}
