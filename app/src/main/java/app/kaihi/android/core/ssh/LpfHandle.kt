package app.kaihi.android.core.ssh

interface LpfHandle {
    val localPort: Int
    suspend fun stop()
}