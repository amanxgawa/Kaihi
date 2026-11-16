package app.kaihi.android.feature.wizard.impl.domain.ssh

import app.kaihi.android.core.ssh.command.BashScriptCommand
import app.kaihi.android.core.ssh.model.CommandResult

internal class SecureXrayConfigCommand : BashScriptCommand<Unit> {
    override val script: String = SCRIPT

    override fun parseOutput(result: CommandResult) = Unit

    private companion object {
        const val SCRIPT = """set -eu
chown root:xray /usr/local/etc/xray /usr/local/etc/xray/config.json
chmod 0750 /usr/local/etc/xray
chmod 0640 /usr/local/etc/xray/config.json"""
    }
}
