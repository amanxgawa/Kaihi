package app.kaihi.android.core.ssh.model

import app.kaihi.android.core.security.Secret

sealed class SshAuth {
    data class Password(val password: Secret) : SshAuth()
    data class PrivateKey(val content: Secret, val passphrase: Secret? = null) : SshAuth()
}
