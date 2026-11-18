package app.kaihi.android.feature.wizard

import app.kaihi.android.core.ssh.model.CommandResult
import app.kaihi.android.core.vpn.XrayProtectionState
import app.kaihi.android.core.vpn.XrayServerProtection
import app.kaihi.android.feature.wizard.impl.domain.ssh.ProtectServerCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectServerCommandTest {
    private val command = ProtectServerCommand()

    @Test
    fun `parses each protection result independently`() {
        assertEquals(
            protection(XrayProtectionState.ENABLED, XrayProtectionState.ENABLED),
            parse("guard=enabled\nupdates=enabled"),
        )
        assertEquals(
            protection(XrayProtectionState.FAILED, XrayProtectionState.ENABLED),
            parse("guard=failed\nupdates=enabled"),
        )
    }

    @Test
    fun `missing or non-exact result lines are failures`() {
        assertEquals(protection(XrayProtectionState.FAILED, XrayProtectionState.FAILED), parse(""))
        assertEquals(
            protection(XrayProtectionState.FAILED, XrayProtectionState.ENABLED),
            parse("guard=enabledX\nupdates=enabled"),
        )
        assertEquals(
            protection(XrayProtectionState.FAILED, XrayProtectionState.ENABLED),
            parse("guard=ENABLED\nupdates=enabled"),
        )
    }

    @Test
    fun `ignores noise surrounding exact result lines`() {
        assertEquals(
            protection(XrayProtectionState.ENABLED, XrayProtectionState.FAILED),
            parse("Installing packages...\nguard=enabled\nwarning: apt mirror\nupdates=failed\ndone"),
        )
    }

    @Test
    fun `script enables sshguard and configures security updates without enabling nftables`() {
        val lines = command.script.lineSequence().map(String::trim).toList()

        assertTrue("&& systemctl enable --now sshguard >/dev/null 2>&1 \\" in lines)
        assertTrue(command.script.contains("/etc/apt/apt.conf.d/20auto-upgrades"))
        assertTrue(command.script.contains("APT::Periodic::Update-Package-Lists \"1\";"))
        assertTrue(command.script.contains("APT::Periodic::Unattended-Upgrade \"1\";"))
        assertFalse(command.script.contains("Automatic-Reboot"))
        assertTrue("  && nft list table ip sshguard >/dev/null 2>&1; then" in command.script.lines())
        assertFalse(command.script.contains("nftables.service"))
    }

    private fun parse(stdout: String) = command.parseOutput(CommandResult(stdout, stderr = "", exitCode = 0))

    private fun protection(guard: XrayProtectionState, updates: XrayProtectionState) =
        XrayServerProtection(bruteForceGuard = guard, securityUpdates = updates)
}
