package app.kaihi.android.core.ssh.internal

import app.kaihi.android.core.security.Secret
import app.kaihi.android.core.ssh.internal.util.SecurityUtils
import app.kaihi.android.core.ssh.model.SshAuth
import app.kaihi.android.core.ssh.model.SshConfig
import app.kaihi.android.core.storage.AppStorage
import app.kaihi.android.core.storage.StorageDomain
import java.io.IOException
import java.security.KeyPairGenerator
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.keyprovider.KeyProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SshjManagerTest {
    private val hostKey = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair().public
    private val fingerprint = SecurityUtils.getFingerprint(hostKey)
    private val config = SshConfig("server.example", username = "root", expectedFingerprint = fingerprint)
    private val auth = SshAuth.Password(Secret("password"))

    @Test
    fun `authenticates using pasted private key contents without reading a key file`() = runTest {
        val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val pem = "-----BEGIN PRIVATE KEY-----\n" +
            Base64.getMimeEncoder(64, byteArrayOf(10)).encodeToString(pair.private.encoded) +
            "\n-----END PRIVATE KEY-----\n"
        val client = FakeClient(false)
        val manager = SshjManager(StandardTestDispatcher(testScheduler), MemoryStorage()) { client }

        manager.withConnection(config, SshAuth.PrivateKey(Secret(pem))) {
            assertTrue(client.authenticated)
            assertEquals(pair.private, client.loadedPrivateKey)
            Result.success(Unit)
        }.getOrThrow()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `failed authentication must not create a trusted host pin`() = runTest {
        val storage = MemoryStorage()
        val client = FakeClient(failAuthentication = true)
        val manager = SshjManager(StandardTestDispatcher(testScheduler), storage) { client }

        assertTrue(manager.withConnection(config, auth) { Result.success(Unit) }.isFailure)
        assertTrue("Unauthenticated host key was persisted", storage.values.isEmpty())
    }

    @Test
    fun `failed authentication must not replace an existing trusted host pin`() = runTest {
        val storage = MemoryStorage().apply { values["ssh.trusted-host.server.example:22"] = "SHA256:old" }
        val manager = SshjManager(StandardTestDispatcher(testScheduler), storage) { FakeClient(true) }

        assertTrue(manager.withConnection(config, auth) { Result.success(Unit) }.isFailure)
        assertEquals("SHA256:old", storage.values.getValue("ssh.trusted-host.server.example:22"))
    }

    @Test
    fun `successful authentication persists the accepted fingerprint before using the connection`() = runTest {
        val storage = MemoryStorage()
        val client = FakeClient(false)
        val manager = SshjManager(StandardTestDispatcher(testScheduler), storage) { client }

        manager.withConnection(config, auth) {
            assertTrue(client.authenticated)
            assertEquals(fingerprint, storage.values.getValue("ssh.trusted-host.server.example:22"))
            Result.success(Unit)
        }.getOrThrow()
        testScheduler.advanceUntilIdle()
    }

    @Test
    fun `closes the SSH client after authentication fails`() = runTest {
        val client = FakeClient(true)
        val manager = SshjManager(StandardTestDispatcher(testScheduler), MemoryStorage()) { client }

        assertTrue(manager.withConnection(config, auth) { Result.success(Unit) }.isFailure)
        assertTrue("Failed SSH client was left open", client.closed)
    }

    @Test
    fun `closes the SSH client after host verification fails`() = runTest {
        val client = FakeClient(false)
        val manager = SshjManager(StandardTestDispatcher(testScheduler), MemoryStorage()) { client }

        assertTrue(manager.withConnection(config.copy(expectedFingerprint = null), auth) { Result.success(Unit) }.isFailure)
        assertFalse(client.authenticated)
        assertTrue("Rejected SSH client was left open", client.closed)
    }

    @Test
    fun `connection setup propagates cancellation and closes the client`() = runTest {
        val client = FakeClient(false, CancellationException("Setup cancelled"))
        val manager = SshjManager(StandardTestDispatcher(testScheduler), MemoryStorage()) { client }

        try {
            manager.withConnection(config, auth) { Result.success(Unit) }
            fail("Cancellation was swallowed")
        } catch (error: CancellationException) {
            assertEquals("Setup cancelled", error.message)
            assertTrue("Cancelled SSH client was left open", client.closed)
        }
    }

    private inner class FakeClient(
        private val failAuthentication: Boolean,
        private val authenticationError: Throwable? = null,
    ) : SSHClient() {
        private lateinit var verifier: HostKeyVerifier
        var authenticated = false
        var closed = false
        var loadedPrivateKey: java.security.PrivateKey? = null

        override fun addHostKeyVerifier(verifier: HostKeyVerifier) { this.verifier = verifier }
        override fun connect(hostname: String, port: Int) {
            check(verifier.verify(hostname, port, hostKey))
        }
        override fun authPassword(username: String, password: String) {
            authenticationError?.let { throw it }
            if (failAuthentication) throw IOException("Authentication rejected")
            authenticated = true
        }
        override fun isConnected(): Boolean = !closed
        override fun authPublickey(username: String, vararg keyProviders: KeyProvider) {
            loadedPrivateKey = keyProviders.single().private
            authenticated = true
        }
        override fun disconnect() { closed = true }
        override fun close() { closed = true }
    }

    private class MemoryStorage : AppStorage {
        val values = mutableMapOf<String, String>()
        override fun getString(domain: StorageDomain, key: String): String? = values[key]
        override fun putString(domain: StorageDomain, key: String, value: String) { values[key] = value }
        override fun remove(domain: StorageDomain, key: String) { values.remove(key) }
        override fun getBoolean(domain: StorageDomain, key: String): Boolean = error("Unused")
        override fun putBoolean(domain: StorageDomain, key: String, value: Boolean) = error("Unused")
    }
}
