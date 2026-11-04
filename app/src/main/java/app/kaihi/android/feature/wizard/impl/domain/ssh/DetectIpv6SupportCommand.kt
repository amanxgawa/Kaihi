package app.kaihi.android.feature.wizard.impl.domain.ssh

import app.kaihi.android.core.ssh.command.BashScriptCommand
import app.kaihi.android.core.ssh.model.CommandResult

internal class DetectIpv6SupportCommand : BashScriptCommand<Boolean> {
    override val script: String = """
        set -eu
        if [ -e /proc/net/if_inet6 ]; then echo ipv6; else echo ipv4; fi
    """.trimIndent()

    override fun parseOutput(result: CommandResult): Boolean = when (result.stdout.trim()) {
        "ipv6" -> true
        "ipv4" -> false
        else -> error("Could not determine whether the server supports IPv6.")
    }
}
