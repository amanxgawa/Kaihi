package app.kaihi.android.feature.wizard.impl.domain.command

import app.kaihi.android.core.ssh.command.BashScriptCommand
import app.kaihi.android.core.ssh.model.CommandResult
import app.kaihi.android.core.vpn.configfactory.XrayServerConfigFactory
import app.kaihi.android.feature.wizard.impl.domain.model.PackageManager
import app.kaihi.android.feature.wizard.impl.domain.model.XrayStartOutcome

internal class StartXrayServiceCommand(
    private val packageManager: PackageManager,
) : BashScriptCommand<XrayStartOutcome> {
    override val script: String = if (packageManager == PackageManager.APK) openRcScript else systemdScript

    override fun parseOutput(result: CommandResult): XrayStartOutcome {
        val lines = result.stdout.lineSequence().toList()
        val markerIndex = lines.indexOfLast { it.startsWith(RESULT_PREFIX) }
        check(markerIndex >= 0) { "Xray start script printed no result" }

        return when (lines[markerIndex].removePrefix(RESULT_PREFIX)) {
            "running" -> XrayStartOutcome.Running
            "stopped" -> XrayStartOutcome.Stopped
            "port-busy" -> XrayStartOutcome.PortBusy(
                lines.drop(markerIndex + 1).firstNotNullOfOrNull(::programName),
            )
            else -> error("Xray start script printed an unknown result")
        }
    }

    private val openRcScript: String
        get() = $$"""
            set -eu
            rc-service xray stop >/dev/null 2>&1 || true
            probe=0
            if command -v timeout >/dev/null 2>&1; then
              timeout 3 bash -c 'exec 3<>/dev/tcp/127.0.0.1/$${XrayServerConfigFactory.DEFAULT_SERVER_PORT}' 2>/dev/null || probe=$?
            else
              (exec 3<>/dev/tcp/127.0.0.1/$${XrayServerConfigFactory.DEFAULT_SERVER_PORT}) 2>/dev/null || probe=$?
            fi
            if [ "$probe" -ne 1 ]; then
              echo "xray-start: port-busy"
              ss -Hltnp "sport = :$${XrayServerConfigFactory.DEFAULT_SERVER_PORT}" 2>/dev/null || netstat -ltnp 2>/dev/null | grep ":$${XrayServerConfigFactory.DEFAULT_SERVER_PORT} " || true
              exit 0
            fi
            rc-update add xray default
            rc-service xray start
            sleep 2
            if rc-service xray status >/dev/null 2>&1; then
              echo "xray-start: running"
            else
              echo "xray-start: stopped"
              tail -n 20 /var/log/xray.log 2>/dev/null || true
            fi
        """.trimIndent()

    private val systemdScript: String
        get() = $$"""
            set -eu
            systemctl daemon-reload
            systemctl stop xray.service >/dev/null 2>&1 || true
            probe=0
            if command -v timeout >/dev/null 2>&1; then
              timeout 3 bash -c 'exec 3<>/dev/tcp/127.0.0.1/$${XrayServerConfigFactory.DEFAULT_SERVER_PORT}' 2>/dev/null || probe=$?
            else
              (exec 3<>/dev/tcp/127.0.0.1/$${XrayServerConfigFactory.DEFAULT_SERVER_PORT}) 2>/dev/null || probe=$?
            fi
            if [ "$probe" -ne 1 ]; then
              echo "xray-start: port-busy"
              ss -Hltnp "sport = :$${XrayServerConfigFactory.DEFAULT_SERVER_PORT}" 2>/dev/null || netstat -ltnp 2>/dev/null | grep ":$${XrayServerConfigFactory.DEFAULT_SERVER_PORT} " || true
              exit 0
            fi
            systemctl enable xray.service
            systemctl start xray.service
            sleep 2
            if [ "$(systemctl show -p SubState --value xray.service)" = running ]; then
              echo "xray-start: running"
            else
              echo "xray-start: stopped"
              journalctl -u xray.service -n 20 --no-pager 2>/dev/null || true
            fi
        """.trimIndent()

    private fun programName(line: String): String? =
        SS_PROGRAM.find(line)?.groupValues?.get(1)
            ?: NETSTAT_PROGRAM.find(line)?.groupValues?.get(1)

    private companion object {
        const val RESULT_PREFIX = "xray-start: "
        val SS_PROGRAM = Regex("users:\\(\\(\"([A-Za-z0-9._-]{1,32})\"")
        val NETSTAT_PROGRAM = Regex("\\b\\d+/([A-Za-z0-9._-]{1,32})\\s*$")
    }
}
