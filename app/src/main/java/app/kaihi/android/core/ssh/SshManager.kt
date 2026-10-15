package app.kaihi.android.core.ssh

import app.kaihi.android.core.ssh.model.SshAuth
import app.kaihi.android.core.ssh.model.SshConfig

interface SshManager {

    suspend fun <T> withConnection(
        config: SshConfig,
        auth: SshAuth,
        block: suspend (SshConnection) -> Result<T>,
    ): Result<T>
}
