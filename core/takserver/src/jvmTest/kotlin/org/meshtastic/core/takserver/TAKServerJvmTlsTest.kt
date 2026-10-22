/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.core.takserver

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.TakCertificateMaterial
import org.meshtastic.core.repository.TakCertificateStore
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManagerFactory
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Loopback mTLS behavior of the real [TAKServerJvm]: only the client certificate this install issued gets in. Every
 * other identity (none, one from an earlier build, another install's, or this install's own server certificate) is
 * turned away and counted, and regeneration swaps which client is accepted.
 */
class TAKServerJvmTlsTest {

    private val dispatchers = CoroutineDispatchers(io = Dispatchers.IO, main = Dispatchers.IO, default = Dispatchers.IO)
    private lateinit var scope: CoroutineScope
    private lateinit var store: InMemoryStore
    private lateinit var manager: TakCertificateManager
    private lateinit var server: TAKServerJvm

    @BeforeTest
    fun setUp() = runBlocking {
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        store = InMemoryStore()
        manager = TakCertificateManager(store)
        server = TAKServerJvm(dispatchers, manager, port = 0)
        server.start(scope).getOrThrow()
    }

    @AfterTest
    fun tearDown() {
        server.stop()
        scope.cancel()
    }

    private val port: Int
        get() = checkNotNull(server.boundPort)

    private val material: TakCertificateMaterial
        get() = checkNotNull(store.saved)

    @Test
    fun `issued client is accepted and counted`() = runBlocking {
        clientContext(material.clientP12, material.password, material.truststoreP12).connect().use { socket ->
            socket.startHandshake()
            awaitCount { server.connectionCount.value == 1 }
        }
        assertEquals(0, server.rejectedConnectionCount.value)
    }

    @Test
    fun `client without a certificate is rejected`() = runBlocking {
        val anonymous =
            SSLContext.getInstance("TLS").apply {
                init(null, trustManagers(material.truststoreP12, material.password), null)
            }
        assertRejected(anonymous)
    }

    @Test
    fun `client identity from an earlier build is rejected`() = runBlocking {
        val legacyClient = resource("legacy_tak_certs/client.p12")
        val legacyCa = resource("legacy_tak_certs/ca.pem")
        // Trust both CAs on the client side so the only thing under test is the server's decision.
        val ctx = clientContext(legacyClient, "meshtastic", material.truststoreP12, extraTrust = legacyCa)
        assertRejected(ctx)
    }

    @Test
    fun `another install's client identity is rejected`() = runBlocking {
        val other = TakCertificateGenerator.generate()
        assertRejected(clientContext(other.clientP12, other.password, material.truststoreP12))
    }

    @Test
    fun `this install's server certificate used as a client is rejected`() = runBlocking {
        assertRejected(clientContext(material.serverP12, material.password, material.truststoreP12))
    }

    @Test
    fun `regenerate swaps the accepted client and keeps listening`() = runBlocking {
        val old = material
        server.certificates.regenerate().getOrThrow()
        val new = material
        assertNotEquals(old.password, new.password)

        assertRejected(clientContext(old.clientP12, old.password, new.truststoreP12))
        clientContext(new.clientP12, new.password, new.truststoreP12).connect().use { socket ->
            socket.startHandshake()
            awaitCount { server.connectionCount.value == 1 }
        }
        assertEquals(0, server.rejectedConnectionCount.value, "accepting the issued client clears the notice")
    }

    @Test
    fun `store failure fails closed`() = runBlocking {
        server.stop()
        val broken =
            object : TakCertificateStore {
                override fun load(): TakCertificateMaterial? = null

                override fun save(material: TakCertificateMaterial) = error("disk full")

                override fun clear() = Unit
            }
        val result = TAKServerJvm(dispatchers, TakCertificateManager(broken), port = 0).start(scope)
        assertTrue(result.isFailure, "server must refuse to start when the identity cannot be persisted")
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private suspend fun assertRejected(ctx: SSLContext) {
        val before = server.rejectedConnectionCount.value
        assertFailsWith<Exception> {
            ctx.connect().use { socket ->
                socket.startHandshake()
                // The server may only close after its own handshake completes; a read surfaces that.
                socket.inputStream.read()
            }
        }
        awaitCount { server.rejectedConnectionCount.value == before + 1 }
        assertEquals(0, server.connectionCount.value)
    }

    private fun SSLContext.connect(): SSLSocket =
        (socketFactory.createSocket("127.0.0.1", port) as SSLSocket).apply { soTimeout = 5_000 }

    private suspend fun awaitCount(condition: () -> Boolean) = withTimeout(5_000) {
        while (!condition()) delay(20)
    }

    private fun clientContext(
        identityP12: ByteArray,
        password: String,
        truststoreP12: ByteArray,
        extraTrust: ByteArray? = null,
    ): SSLContext {
        val pw = password.toCharArray()
        val identity = KeyStore.getInstance("PKCS12").apply { ByteArrayInputStream(identityP12).use { load(it, pw) } }
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(identity, pw) }
        val tms = trustManagers(truststoreP12, material.password, extraTrust)
        return SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, tms, null) }
    }

    private fun trustManagers(truststoreP12: ByteArray, password: String, extraPem: ByteArray? = null) =
        TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            .apply {
                val trust =
                    KeyStore.getInstance("PKCS12").apply {
                        ByteArrayInputStream(truststoreP12).use { load(it, password.toCharArray()) }
                    }
                extraPem?.let { pem ->
                    CertificateFactory.getInstance("X.509")
                        .generateCertificates(ByteArrayInputStream(pem))
                        .filterIsInstance<X509Certificate>()
                        .forEachIndexed { i, cert -> trust.setCertificateEntry("extra-$i", cert) }
                }
                init(trust)
            }
            .trustManagers

    private fun resource(name: String): ByteArray =
        checkNotNull(javaClass.classLoader.getResourceAsStream(name)) { "missing test resource $name" }
            .use { it.readBytes() }

    private class InMemoryStore : TakCertificateStore {
        @Volatile var saved: TakCertificateMaterial? = null

        override fun load(): TakCertificateMaterial? = saved

        override fun save(material: TakCertificateMaterial) {
            saved = material
        }

        override fun clear() {
            saved = null
        }
    }
}
