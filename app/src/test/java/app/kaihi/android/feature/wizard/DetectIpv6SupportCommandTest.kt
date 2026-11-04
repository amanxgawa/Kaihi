package app.kaihi.android.feature.wizard

import app.kaihi.android.core.ssh.model.CommandResult
import app.kaihi.android.feature.wizard.impl.domain.ssh.DetectIpv6SupportCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class DetectIpv6SupportCommandTest {
    private fun result(stdout: String) = CommandResult(stdout = stdout, stderr = "", exitCode = 0)

    @Test
    fun `parses IPv6 support tokens`() {
        val command = DetectIpv6SupportCommand()

        assertEquals(true, command.parseOutput(result("ipv6\n")))
        assertEquals(false, command.parseOutput(result("ipv4\n")))
    }

    @Test
    fun `rejects unexpected output`() {
        assertThrows(IllegalStateException::class.java) {
            DetectIpv6SupportCommand().parseOutput(result("garbage"))
        }
    }

    @Test
    fun `script checks for the kernel IPv6 interface file`() {
        assertEquals(
            "set -eu\nif [ -e /proc/net/if_inet6 ]; then echo ipv6; else echo ipv4; fi",
            DetectIpv6SupportCommand().script,
        )
    }
}
