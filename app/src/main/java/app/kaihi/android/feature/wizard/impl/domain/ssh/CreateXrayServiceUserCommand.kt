package app.kaihi.android.feature.wizard.impl.domain.ssh

import app.kaihi.android.core.ssh.command.BashScriptCommand
import app.kaihi.android.core.ssh.model.CommandResult
import app.kaihi.android.feature.wizard.impl.domain.model.PackageManager

internal class CreateXrayServiceUserCommand(
    packageManager: PackageManager,
) : BashScriptCommand<Unit> {
    override val script: String = if (packageManager == PackageManager.APK) APK_SCRIPT else SYSTEM_SCRIPT

    override fun parseOutput(result: CommandResult) = Unit

    private companion object {
        const val SYSTEM_SCRIPT = $$"""set -eu
nologin="$(command -v nologin || echo /bin/false)"
getent group kaihi-xray >/dev/null || groupadd --system kaihi-xray
getent passwd kaihi-xray >/dev/null || useradd --system --gid kaihi-xray --no-create-home \
  --home-dir /nonexistent --shell "$nologin" kaihi-xray
test "$(id -u kaihi-xray)" -ne 0 && test "$(id -g kaihi-xray)" -ne 0"""

        const val APK_SCRIPT = $$"""set -eu
nologin="$(command -v nologin || echo /bin/false)"
getent group kaihi-xray >/dev/null || addgroup -S kaihi-xray
getent passwd kaihi-xray >/dev/null || adduser -S -D -H -h /nonexistent -s "$nologin" -G kaihi-xray kaihi-xray
test "$(id -u kaihi-xray)" -ne 0 && test "$(id -g kaihi-xray)" -ne 0"""
    }
}
