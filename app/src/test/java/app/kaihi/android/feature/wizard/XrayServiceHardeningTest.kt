package app.kaihi.android.feature.wizard

import app.kaihi.android.feature.wizard.impl.domain.deployment.XrayOpenRcServiceFactory
import app.kaihi.android.feature.wizard.impl.domain.deployment.XraySystemdServiceFactory
import app.kaihi.android.feature.wizard.impl.domain.model.PackageManager
import app.kaihi.android.feature.wizard.impl.domain.ssh.CreateXrayServiceUserCommand
import app.kaihi.android.feature.wizard.impl.domain.ssh.SecureXrayConfigCommand
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XrayServiceHardeningTest {

    @Test
    fun `systemd unit runs as the restricted service user`() {
        val lines = XraySystemdServiceFactory.create().split("\n")

        listOf(
            "User=kaihi-xray",
            "CapabilityBoundingSet=CAP_NET_BIND_SERVICE",
            "AmbientCapabilities=CAP_NET_BIND_SERVICE",
            "NoNewPrivileges=true",
        ).forEach { expected -> assertTrue("Missing exact line: $expected", expected in lines) }
        assertFalse(lines.any { it.startsWith("User=root") })

        val unit = lines.joinToString("\n")
        assertFalse(unit.contains("CAP_NET_ADMIN"))
        assertFalse(unit.contains("CAP_NET_RAW"))
        assertFalse(unit.contains("CAP_SYS_ADMIN"))
    }

    @Test
    fun `OpenRC service sets its user and bind capability`() {
        val lines = XrayOpenRcServiceFactory.create().split("\n")

        assertTrue("command_user=\"kaihi-xray:kaihi-xray\"" in lines)
        assertTrue("capabilities=\"^cap_net_bind_service\"" in lines)
        assertTrue("no_new_privs=\"yes\"" in lines)
    }

    @Test
    fun `service user command selects platform account tools`() {
        val apkScript = CreateXrayServiceUserCommand(PackageManager.APK).script
        assertTrue(apkScript.contains("adduser -S"))
        assertFalse(apkScript.contains("useradd"))
        assertTrue(apkScript.contains(UID_GID_NON_ROOT_CHECK))

        (PackageManager.entries - PackageManager.APK).forEach { packageManager ->
            val script = CreateXrayServiceUserCommand(packageManager).script
            assertTrue("$packageManager must use useradd", script.contains("useradd --system"))
            assertFalse("$packageManager must not use adduser -S", script.contains("adduser -S"))
            assertTrue("$packageManager must reject uid/gid 0", script.contains(UID_GID_NON_ROOT_CHECK))
        }
    }

    @Test
    fun `config permissions make the private key group readable only`() {
        val script = SecureXrayConfigCommand().script

        assertTrue(script.contains("chown root:kaihi-xray"))
        assertTrue(script.contains("chmod 0640 /usr/local/etc/xray/config.json"))
    }

    private companion object {
        const val UID_GID_NON_ROOT_CHECK =
            "test \"\$(id -u kaihi-xray)\" -ne 0 && test \"\$(id -g kaihi-xray)\" -ne 0"
    }
}
