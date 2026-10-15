package app.kaihi.android.core.ssh.model

data class CommandResult(
    val stdout: String,
    val stderr: String,
    val exitCode: Int
)
