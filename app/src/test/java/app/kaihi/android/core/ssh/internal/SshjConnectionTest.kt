package app.kaihi.android.core.ssh.internal

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.sftp.SFTPClient
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class SshjConnectionTest {
    @Test
    fun `upload propagates cancellation`() = runTest {
        val client = object : SSHClient() {
            override fun newSFTPClient(): SFTPClient = throw CancellationException("Upload cancelled")
        }
        val connection = SshjConnection(client, StandardTestDispatcher(testScheduler))

        try {
            connection.upload("/tmp/test", byteArrayOf(), null)
            fail("Cancellation was swallowed")
        } catch (error: CancellationException) {
            assertEquals("Upload cancelled", error.message)
        }
    }

    @Test
    fun `execute propagates cancellation instead of returning a failed result`() = runTest {
        val cancellation = CancellationException("Command cancelled")
        val client = object : SSHClient() {
            override fun startSession(): Session = throw cancellation
        }
        val connection = SshjConnection(client, StandardTestDispatcher(testScheduler))

        try {
            connection.execute("true", emptyList())
            fail("Cancellation was swallowed")
        } catch (error: CancellationException) {
            assertEquals(cancellation.message, error.message)
        }
    }
}
