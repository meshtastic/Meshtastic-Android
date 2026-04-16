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
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/**
 * Loads the bundled TAK server certificates from the classpath and builds an [SSLContext]
 * suitable for running a TLS TAK server with mutual TLS (mTLS).
 *
 * Bundled resources (under `tak_certs/` on the module classpath):
 *  - `server.p12` — PKCS#12 containing the server's identity (cert + private key).
 *    Used as the server's identity during the TLS handshake.
 *  - `client.p12` — PKCS#12 containing an example client identity, included in the
 *    exported data package so ATAK / iTAK have a certificate it can present.
 *  - `ca.pem` — PEM-encoded CA certificate used to validate the presented client
 *    certificate during mTLS. Only clients whose certificate chains back to this CA
 *    are accepted.
 *
 * All files are the same bytes as the iOS Meshtastic-Apple bundle, so the same
 * exported data package works for both platforms with no re-import.
 */
internal object TakCertLoader {

    private const val RESOURCE_SERVER_P12 = "tak_certs/server.p12"
    private const val RESOURCE_CLIENT_P12 = "tak_certs/client.p12"
    private const val RESOURCE_CA_PEM = "tak_certs/ca.pem"

    @Volatile private var cachedSslContext: SSLContext? = null
    @Volatile private var cachedServerP12: ByteArray? = null
    @Volatile private var cachedClientP12: ByteArray? = null
    @Volatile private var cachedCaPem: ByteArray? = null

    /**
     * Build (and cache) an [SSLContext] for the TAK server.
     *
     * The context uses the bundled `server.p12` for its identity and the bundled
     * `ca.pem` to validate client certificates during mTLS. If anything fails to
     * load (missing resources, bad password, corrupt keystore) this returns `null`
     * and callers should fall back to a non-TLS listener or refuse to start.
     */
    @Synchronized
    fun getServerSslContext(): SSLContext? {
        cachedSslContext?.let { return it }
        return try {
            val serverP12 = loadResourceBytes(RESOURCE_SERVER_P12)
                ?: error("Bundled $RESOURCE_SERVER_P12 not found on classpath")
            val caPem = loadResourceBytes(RESOURCE_CA_PEM)
                ?: error("Bundled $RESOURCE_CA_PEM not found on classpath")

            // Load the server identity (cert + private key).
            val serverKeyStore = KeyStore.getInstance("PKCS12").apply {
                ByteArrayInputStream(serverP12).use { input ->
                    load(input, TAK_BUNDLED_CERT_PASSWORD.toCharArray())
                }
            }
            val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(serverKeyStore, TAK_BUNDLED_CERT_PASSWORD.toCharArray())
            }

            // Load the CA certificate(s) used to verify incoming client certs.
            val caCerts = parsePemCertificates(caPem)
            if (caCerts.isEmpty()) error("No certificates found inside $RESOURCE_CA_PEM")
            val trustKeyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                load(null, null)
                caCerts.forEachIndexed { index, cert ->
                    setCertificateEntry("tak-client-ca-$index", cert)
                }
            }
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                init(trustKeyStore)
            }

            val sslContext = SSLContext.getInstance("TLSv1.2").apply {
                init(kmf.keyManagers, tmf.trustManagers, null)
            }

            Logger.i { "TAK: loaded bundled TLS server identity and ${caCerts.size} CA certificate(s)" }
            cachedSslContext = sslContext
            sslContext
        } catch (e: Throwable) {
            Logger.e(e) { "TAK: failed to build SSLContext from bundled certificates: ${e.message}" }
            null
        }
    }

    /** Returns the raw bytes of the bundled `server.p12`. Used by the data package generator. */
    fun getServerP12Bytes(): ByteArray? {
        cachedServerP12?.let { return it }
        val bytes = loadResourceBytes(RESOURCE_SERVER_P12)
        cachedServerP12 = bytes
        return bytes
    }

    /** Returns the raw bytes of the bundled `client.p12`. Used by the data package generator. */
    fun getClientP12Bytes(): ByteArray? {
        cachedClientP12?.let { return it }
        val bytes = loadResourceBytes(RESOURCE_CLIENT_P12)
        cachedClientP12 = bytes
        return bytes
    }

    /** Returns the raw bytes of the bundled `ca.pem`. */
    fun getCaPemBytes(): ByteArray? {
        cachedCaPem?.let { return it }
        val bytes = loadResourceBytes(RESOURCE_CA_PEM)
        cachedCaPem = bytes
        return bytes
    }

    private fun loadResourceBytes(name: String): ByteArray? {
        val stream = TakCertLoader::class.java.classLoader?.getResourceAsStream(name)
            ?: return null
        return stream.use { it.readBytes() }
    }

    /**
     * Parse every `-----BEGIN CERTIFICATE----- ... -----END CERTIFICATE-----` block in the
     * given PEM bytes into [X509Certificate]s. Tolerates multiple certs in one file.
     */
    private fun parsePemCertificates(pem: ByteArray): List<X509Certificate> {
        val factory = CertificateFactory.getInstance("X.509")
        // CertificateFactory.generateCertificates handles PEM bundles directly on all
        // standard Java providers, so we don't need to split ourselves.
        return ByteArrayInputStream(pem).use { input ->
            factory.generateCertificates(input).filterIsInstance<X509Certificate>()
        }
    }
}
