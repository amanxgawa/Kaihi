package app.kaihi.android.feature.wizard.impl.domain.ssh

import app.kaihi.android.core.ssh.command.BashScriptCommand
import app.kaihi.android.core.ssh.model.CommandResult
import app.kaihi.android.core.vpn.XrayProtectionState
import app.kaihi.android.core.vpn.XrayServerProtection

internal class ProtectServerCommand : BashScriptCommand<XrayServerProtection> {
    override val script: String = SCRIPT

    override fun parseOutput(result: CommandResult): XrayServerProtection {
        var guard = XrayProtectionState.FAILED
        var updates = XrayProtectionState.FAILED
        result.stdout.lineSequence().forEach { line ->
            when (line) {
                "guard=enabled" -> guard = XrayProtectionState.ENABLED
                "guard=failed" -> guard = XrayProtectionState.FAILED
                "updates=enabled" -> updates = XrayProtectionState.ENABLED
                "updates=failed" -> updates = XrayProtectionState.FAILED
            }
        }
        return XrayServerProtection(bruteForceGuard = guard, securityUpdates = updates)
    }

    private companion object {
        val SCRIPT = $$"""
            set -u
            export DEBIAN_FRONTEND=noninteractive
            apt_install() { apt-get -o DPkg::Lock::Timeout=120 install -y -qq "$@" >/dev/null 2>&1; }

            guard=failed
            if apt_install sshguard \
              && systemctl enable --now sshguard >/dev/null 2>&1 \
              && systemctl is-active --quiet sshguard \
              && nft list table ip sshguard >/dev/null 2>&1; then
              guard=enabled
            fi

            updates=failed
            if apt_install unattended-upgrades \
              && printf '%s\n' \
                'APT::Periodic::Update-Package-Lists "1";' \
                'APT::Periodic::Unattended-Upgrade "1";' \
                > /etc/apt/apt.conf.d/20auto-upgrades; then
              updates=enabled
            fi

            echo "guard=$guard"
            echo "updates=$updates"
        """.trimIndent()
    }
}
