package app.kaihi.android.feature.wizard.impl.domain.ssh

import app.kaihi.android.core.ssh.command.BashScriptCommand
import app.kaihi.android.core.ssh.model.CommandResult
import app.kaihi.android.feature.wizard.impl.domain.model.PackageManager

internal class StartXrayServiceCommand(
    private val packageManager: PackageManager,
) : BashScriptCommand<Unit> {
    override val script: String = if (packageManager == PackageManager.APK) openRcScript else systemdScript

    override fun parseOutput(result: CommandResult) = Unit

    private val openRcScript: String
        get() = """
            set -eu
            rc-update add kaihi-xray default
            rc-service kaihi-xray restart
            rc-service kaihi-xray status
        """.trimIndent()

    private val systemdScript: String
        get() = """
            set -eu
            systemctl daemon-reload
            systemctl enable kaihi-xray.service
            systemctl restart kaihi-xray.service
            systemctl is-active --quiet kaihi-xray.service
        """.trimIndent()
}
