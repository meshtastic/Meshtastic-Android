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
@file:Suppress("TooGenericExceptionCaught")

package org.meshtastic.core.takserver

import co.touchlab.kermit.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.meshtastic.core.repository.TakCertificateMaterial
import org.meshtastic.core.repository.TakCertificateStore
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/**
 * Owns the per-install TAK PKI: loads it from the encrypted [TakCertificateStore], generates and saves a fresh set when
 * none exists (or the stored one is unreadable), and turns it into the [SSLContext] plus pinned client certificate the
 * listener needs.
 *
 * Trust is deliberately narrower than "chains to our CA": [isIssuedClient] requires the presented leaf to be
 * byte-identical to the one client certificate this install issued. The CA key no longer exists, so nothing else could
 * chain anyway, but pinning keeps that true even if the store were ever restored from another install's backup.
 */
internal class TakCertificateManager(
    private val store: TakCertificateStore,
    private val generate: () -> TakCertificateMaterial = { TakCertificateGenerator.generate() },
) {

    /** A loaded PKI ready for the listener. */
    class Loaded(
        val material: TakCertificateMaterial,
        val sslContext: SSLContext,
        val clientCertificate: X509Certificate,
    )

    private val mutex = Mutex()

    @Volatile private var cached: Loaded? = null

    /** The current PKI, generating and persisting one on first use. Throws when generation or storage fails. */
    suspend fun getOrCreate(): Loaded {
        cached?.let {
            return it
        }
        return mutex.withLock {
            cached
                ?: run {
                    // A store that cannot decrypt (Android keystore reset, restored backup, damaged file) must
                    // lead to a clean reissue, not a server that fails to start on every retry.
                    val stored =
                        try {
                            store.load()
                        } catch (e: Exception) {
                            Logger.w(e) { "TAK: could not read stored certificates, issuing a new set" }
                            null
                        }
                    val loaded = stored?.let { material ->
                        try {
                            load(material)
                        } catch (e: Exception) {
                            Logger.w(e) { "TAK: stored certificates are unusable, issuing a new set" }
                            null
                        }
                    }
                    (loaded ?: issue()).also { cached = it }
                }
        }
    }

    /** Discards the current PKI and issues a new one. Throws when generation or storage fails. */
    suspend fun regenerate(): Loaded = mutex.withLock { issue().also { cached = it } }

    /** Whether [peer] is exactly the client certificate this install issued. */
    fun isIssuedClient(peer: Certificate?): Boolean {
        val expected = cached?.clientCertificate ?: return false
        return peer != null && expected.encoded.contentEquals(peer.encoded)
    }

    private fun issue(): Loaded {
        Logger.i { "TAK: generating per-install server, client and CA certificates" }
        val material = generate()
        val loaded = load(material)
        store.save(material)
        return loaded
    }

    private fun load(material: TakCertificateMaterial): Loaded {
        val password = material.password.toCharArray()

        val serverKeyStore = pkcs12(material.serverP12, password)
        val kmf =
            KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(serverKeyStore, password)
            }

        val trustStore = pkcs12(material.truststoreP12, password)
        check(trustStore.aliases().asSequence().any { trustStore.isCertificateEntry(it) }) {
            "TAK truststore holds no CA certificate"
        }
        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trustStore) }

        val clientKeyStore = pkcs12(material.clientP12, password)
        val clientAlias =
            clientKeyStore.aliases().asSequence().firstOrNull { clientKeyStore.isKeyEntry(it) }
                ?: error("TAK client.p12 holds no key entry")
        val clientCertificate =
            clientKeyStore.getCertificateChain(clientAlias).firstOrNull() as? X509Certificate
                ?: error("TAK client.p12 holds no certificate")

        val sslContext = SSLContext.getInstance("TLSv1.2").apply { init(kmf.keyManagers, tmf.trustManagers, null) }
        return Loaded(material, sslContext, clientCertificate)
    }

    private fun pkcs12(bytes: ByteArray, password: CharArray): KeyStore =
        KeyStore.getInstance("PKCS12").apply { ByteArrayInputStream(bytes).use { load(it, password) } }
}
