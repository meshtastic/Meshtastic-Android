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

import kotlinx.coroutines.test.runTest
import org.meshtastic.core.repository.TakCertificateMaterial
import org.meshtastic.core.repository.TakCertificateStore
import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The generated `.p12` containers must stay encoded with legacy PKCS#12 algorithms (SHA1/3DES bags, SHA1 MAC). Android
 * 9 and older cannot parse PBES2/AES-encrypted containers, which breaks ATAK data package import (#6567).
 */
class TakCertificateGeneratorTest {

    // DER encoding of OID 1.2.840.113549.1.5.13 (PBES2): must NOT appear in the containers.
    private val pbes2Oid =
        byteArrayOf(0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x05, 0x0D)

    // DER encoding of OID 1.2.840.113549.1.12.1.3 (pbeWithSHA1And3-KeyTripleDES-CBC): must appear on key bags.
    private val sha1TripleDesOid =
        byteArrayOf(0x06, 0x0A, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x0C, 0x01, 0x03)

    // DER prefix of the MacData DigestInfo for a SHA1 MAC: AlgorithmIdentifier(1.3.14.3.2.26, NULL)
    // followed by the 20-byte OCTET STRING header: must appear.
    private val sha1MacDigestInfoPrefix =
        byteArrayOf(0x30, 0x09, 0x06, 0x05, 0x2B, 0x0E, 0x03, 0x02, 0x1A, 0x05, 0x00, 0x04, 0x14)

    private val material: TakCertificateMaterial by lazy { TakCertificateGenerator.generate() }

    private fun ByteArray.containsSequence(needle: ByteArray): Boolean =
        (0..size - needle.size).any { offset -> needle.indices.all { this[offset + it] == needle[it] } }

    private fun assertLegacyEncoding(bytes: ByteArray, name: String) {
        assertFalse(bytes.containsSequence(pbes2Oid), "$name uses PBES2/AES encryption, unreadable on Android <= 9")
        assertTrue(bytes.containsSequence(sha1TripleDesOid), "$name is missing legacy SHA1/3DES PKCS#12 encryption")
        assertTrue(bytes.containsSequence(sha1MacDigestInfoPrefix), "$name is missing a SHA1 PKCS#12 MAC")
    }

    /** Loads with the platform's stock PKCS#12 parser, the one the server and ATAK actually use. */
    private fun loadPkcs12(bytes: ByteArray, password: String = material.password): KeyStore =
        KeyStore.getInstance("PKCS12").apply { ByteArrayInputStream(bytes).use { load(it, password.toCharArray()) } }

    private fun KeyStore.privateKeys(): List<PrivateKey> =
        aliases().toList().mapNotNull { getKey(it, material.password.toCharArray()) as? PrivateKey }

    private fun KeyStore.leafCertificate(): X509Certificate = aliases().toList().firstNotNullOf { alias ->
        getCertificateChain(alias)?.firstOrNull() as? X509Certificate ?: getCertificate(alias) as? X509Certificate
    }

    @Test
    fun `server and client p12 use legacy encoding and load with a private key`() {
        assertLegacyEncoding(material.serverP12, "server.p12")
        assertLegacyEncoding(material.clientP12, "client.p12")
        assertTrue(loadPkcs12(material.serverP12).privateKeys().isNotEmpty(), "server.p12 has no private key entry")
        assertTrue(loadPkcs12(material.clientP12).privateKeys().isNotEmpty(), "client.p12 has no private key entry")
    }

    @Test
    fun `truststore holds the CA certificate and no private key`() {
        assertLegacyEncoding(material.truststoreP12, "truststore.p12")
        val trust = loadPkcs12(material.truststoreP12)
        assertTrue(trust.privateKeys().isEmpty(), "truststore.p12 holds only certificates")
        val ca = trust.leafCertificate()
        assertTrue(ca.basicConstraints >= 0, "truststore entry is not a CA certificate")
        assertEquals(ca.subjectX500Principal, ca.issuerX500Principal, "CA must be self-signed")
    }

    @Test
    fun `leaves chain to the CA and carry the right usages`() {
        val ca = loadPkcs12(material.truststoreP12).leafCertificate()
        val server = loadPkcs12(material.serverP12).leafCertificate()
        val client = loadPkcs12(material.clientP12).leafCertificate()

        server.verify(ca.publicKey)
        client.verify(ca.publicKey)
        assertEquals(-1, server.basicConstraints, "server leaf must not be a CA")
        assertEquals(-1, client.basicConstraints, "client leaf must not be a CA")
        assertTrue("1.3.6.1.5.5.7.3.1" in server.extendedKeyUsage.orEmpty(), "server leaf needs serverAuth")
        assertTrue("1.3.6.1.5.5.7.3.2" in client.extendedKeyUsage.orEmpty(), "client leaf needs clientAuth")
        val sans = server.subjectAlternativeNames.orEmpty().map { it[1].toString() }
        assertTrue("localhost" in sans && "127.0.0.1" in sans, "server SAN must cover loopback: $sans")
    }

    @Test
    fun `every install gets distinct material and password`() {
        val other = TakCertificateGenerator.generate()
        assertNotEquals(material.password, other.password)
        assertNotEquals(
            loadPkcs12(material.clientP12).leafCertificate(),
            loadPkcs12(other.clientP12, other.password).leafCertificate(),
        )
        assertEquals(TAK_CERT_PASSWORD_LENGTH, material.password.length)
    }

    @Test
    fun `manager persists generated material and reloads it`() = runTest {
        val store = InMemoryStore()
        var generations = 0
        val manager =
            TakCertificateManager(store) {
                generations++
                TakCertificateGenerator.generate()
            }

        val first = manager.getOrCreate()
        assertEquals(1, generations)
        val saved = assertNotNull(store.saved, "material must be persisted")
        assertContentEquals(first.material.serverP12, saved.serverP12)
        assertContentEquals(first.material.clientP12, saved.clientP12)
        assertContentEquals(first.material.truststoreP12, saved.truststoreP12)
        assertEquals(first.material.password, saved.password)
        assertTrue(manager.isIssuedClient(first.clientCertificate))

        // A fresh manager over the same store must reuse the stored set rather than issue again.
        val reloaded =
            TakCertificateManager(store) {
                generations++
                TakCertificateGenerator.generate()
            }
                .getOrCreate()
        assertEquals(1, generations)
        assertEquals(first.clientCertificate, reloaded.clientCertificate)
    }

    @Test
    fun `manager replaces corrupt material and pins only the current client`() = runTest {
        val store = InMemoryStore()
        store.save(TakCertificateMaterial(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3), "bad"))
        val manager = TakCertificateManager(store)

        val first = manager.getOrCreate()
        assertNotEquals("bad", store.saved?.password)

        val second = manager.regenerate()
        assertFalse(manager.isIssuedClient(first.clientCertificate), "old client must be rejected after regenerate")
        assertTrue(manager.isIssuedClient(second.clientCertificate))
        assertFalse(manager.isIssuedClient(null))
    }

    @Test
    fun `manager treats a throwing store as empty`() = runTest {
        val store =
            object : TakCertificateStore {
                var saved: TakCertificateMaterial? = null

                override fun load(): TakCertificateMaterial? = saved ?: error("keystore reset")

                override fun save(material: TakCertificateMaterial) {
                    saved = material
                }

                override fun clear() {
                    saved = null
                }
            }
        val loaded = TakCertificateManager(store).getOrCreate()
        assertNotNull(store.saved)
        assertTrue(TakCertificateManager(store).isIssuedClient(null).not())
        assertEquals(loaded.clientCertificate, TakCertificateManager(store).getOrCreate().clientCertificate)
    }

    private class InMemoryStore : TakCertificateStore {
        var saved: TakCertificateMaterial? = null

        override fun load(): TakCertificateMaterial? = saved

        override fun save(material: TakCertificateMaterial) {
            saved = material
        }

        override fun clear() {
            saved = null
        }
    }

    @Test
    fun `data package carries client and CA-only truststore with the install password`() {
        val credentials =
            TakDataPackageCredentials(
                clientP12 = material.clientP12,
                truststoreP12 = material.truststoreP12,
                password = material.password,
            )
        val pref = TAKDataPackageGenerator.generateConfigPref(certPassword = credentials.password)
        assertTrue(pref.contains("127.0.0.1:8089:ssl"))
        assertTrue(pref.contains(material.password))
        assertFalse(pref.contains("meshtastic</entry>"), "pref uses this install's password")
        assertNull(Regex("key=\"caPassword\"[^>]*>meshtastic<").find(pref))

        val zip = TAKDataPackageGenerator.generateDataPackage(credentials)
        val entries = readZipEntries(zip)
        assertEquals(setOf("meshtastic-server.pref", "manifest.xml", "truststore.p12", "client.p12"), entries.keys)
        assertContentEquals(material.truststoreP12, entries.getValue("truststore.p12"))
        assertContentEquals(material.clientP12, entries.getValue("client.p12"))
        assertFalse(
            entries.values.any { it.containsSequence(material.serverP12) },
            "package carries only the client and truststore",
        )
        val packagedPref = entries.getValue("meshtastic-server.pref").decodeToString()
        assertTrue(packagedPref.contains("""key="caPassword" class="class java.lang.String">${material.password}<"""))
        assertTrue(
            packagedPref.contains("""key="clientPassword" class="class java.lang.String">${material.password}<"""),
        )
        assertTrue(loadPkcs12(entries.getValue("truststore.p12")).privateKeys().isEmpty())
        assertTrue(loadPkcs12(entries.getValue("client.p12")).privateKeys().isNotEmpty())
    }

    private fun readZipEntries(zip: ByteArray): Map<String, ByteArray> =
        java.util.zip.ZipInputStream(ByteArrayInputStream(zip)).use { input ->
            generateSequence { input.nextEntry }.associate { entry -> entry.name to input.readBytes() }
        }
}
