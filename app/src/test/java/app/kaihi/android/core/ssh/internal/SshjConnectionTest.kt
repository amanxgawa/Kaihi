package app.kaihi.android.core.ssh.internal

import java.net.ServerSocket
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.connection.channel.direct.LocalPortForwarder
import net.schmizz.sshj.connection.channel.direct.Parameters
import net.schmizz.sshj.sftp.SFTPClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun `port forwarding propagates cancellation and closes its listener`() = runTest {
        lateinit var listener: ServerSocket
        val client = object : SSHClient() {
            override fun newLocalPortForwarder(params: Parameters, serverSocket: ServerSocket): LocalPortForwarder {
                listener = serverSocket
                throw CancellationException("Forwarding cancelled")
            }
        }
        val connection = SshjConnection(client, StandardTestDispatcher(testScheduler))

        try {
            try {
                connection.startLpf(443)
                fail("Cancellation was swallowed")
            } catch (error: CancellationException) {
                assertEquals("Forwarding cancelled", error.message)
                assertTrue("Cancelled forwarding left its listener open", listener.isClosed)
            }
        } finally {
            listener.close()
        }
    }

    @Test
    fun `failed port forwarding closes its listener`() = runTest {
        lateinit var listener: ServerSocket
        val client = object : SSHClient() {
            override fun newLocalPortForwarder(params: Parameters, serverSocket: ServerSocket): LocalPortForwarder {
                listener = serverSocket
                throw IllegalStateException("Disconnected")
            }
        }
        val connection = SshjConnection(client, StandardTestDispatcher(testScheduler))

        try {
            assertTrue(connection.startLpf(443).isFailure)
            assertTrue("Failed forwarding left its listener open", listener.isClosed)
        } finally {
            listener.close()
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
