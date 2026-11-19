package app.kaihi.android.feature.wizard

import app.kaihi.android.core.ssh.model.CommandResult
import app.kaihi.android.feature.wizard.impl.domain.model.PackageManager
import app.kaihi.android.feature.wizard.impl.domain.model.XrayStartOutcome
import app.kaihi.android.feature.wizard.impl.domain.command.StartXrayServiceCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartXrayServiceCommandTest {

    @Test
    fun `Alpine's package manager selects an OpenRC script, not systemd`() {
        val script = StartXrayServiceCommand(PackageManager.APK).script

        assertTrue(script.contains("rc-service"))
        assertFalse(script.contains("systemctl"))
    }

    @Test
    fun `every other package manager selects the systemd script`() {
        val nonAlpine = PackageManager.entries - PackageManager.APK

        nonAlpine.forEach { packageManager ->
            val script = StartXrayServiceCommand(packageManager).script
            assertTrue("$packageManager should get systemctl", script.contains("systemctl"))
            assertFalse("$packageManager should not get rc-service", script.contains("rc-service"))
        }
    }

    @Test
    fun `parses running marker`() {
        assertEquals(XrayStartOutcome.Running, parse("xray-start: running\n"))
    }

    @Test
    fun `parses stopped marker and ignores following journal lines`() {
        assertEquals(
            XrayStartOutcome.Stopped,
            parse("xray-start: stopped\nJan 1 xray[1]: listen tcp :443: bind: address already in use\n"),
        )
    }

    @Test
    fun `extracts program name from ss output`() {
        assertEquals(
            XrayStartOutcome.PortBusy("nginx"),
            parse("xray-start: port-busy\nLISTEN 0 511 0.0.0.0:443 0.0.0.0:* users:((\"nginx\",pid=812,fd=6))\n"),
        )
    }

    @Test
    fun `extracts program name from netstat output`() {
        assertEquals(
            XrayStartOutcome.PortBusy("caddy"),
            parse("xray-start: port-busy\ntcp 0 0 0.0.0.0:443 0.0.0.0:* LISTEN 812/caddy\n"),
        )
    }

    @Test
    fun `parses busy port without a program name`() {
        assertEquals(XrayStartOutcome.PortBusy(null), parse("xray-start: port-busy\n"))
    }

    @Test
    fun `does not expose program names containing forbidden characters`() {
        assertEquals(
            XrayStartOutcome.PortBusy(null),
            parse("xray-start: port-busy\nLISTEN users:((\"ng<b>inx\",pid=1,fd=3))\n"),
        )
    }

    @Test(expected = IllegalStateException::class)
    fun `requires a result marker`() {
        parse("journal output without a result marker\n")
    }

    @Test
    fun `service scripts check the port and wait for startup`() {
        val apkScript = StartXrayServiceCommand(PackageManager.APK).script
        val systemdScript = StartXrayServiceCommand(PackageManager.APT).script

        listOf(apkScript, systemdScript).forEach { script ->
            assertTrue(script.contains("sleep 2"))
            assertTrue(script.contains("xray-start: running"))
            assertTrue(script.contains("xray-start: port-busy"))
            assertTrue(script.contains("/dev/tcp/127.0.0.1/443"))
            assertTrue(script.contains("timeout 3 bash -c 'exec 3<>/dev/tcp/127.0.0.1/443'"))
            assertTrue(script.contains("if [ \"\$probe\" -ne 1 ]; then"))
            assertFalse(script.contains("if (exec 3<>/dev/tcp"))
            assertFalse(script.contains("restart"))
        }
        assertTrue(systemdScript.contains("SubState"))
    }

    private fun parse(stdout: String): XrayStartOutcome =
        StartXrayServiceCommand(PackageManager.APT).parseOutput(CommandResult(stdout, "", 0))
}
