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

import java.io.ByteArrayInputStream
import java.security.KeyStore
import java.security.PrivateKey
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The bundled `.p12` containers must stay encoded with legacy PKCS#12 algorithms (SHA1/3DES bags, SHA1 MAC). Android 9
 * and older cannot parse PBES2/AES-encrypted containers, which breaks ATAK data package import (#6567).
 */
class TakCertLegacyEncodingTest {

    // DER encoding of OID 1.2.840.113549.1.5.13 (PBES2) — must NOT appear in the containers.
    private val pbes2Oid =
        byteArrayOf(0x06, 0x09, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x05, 0x0D)

    // DER encoding of OID 1.2.840.113549.1.12.1.3 (pbeWithSHA1And3-KeyTripleDES-CBC) — must appear.
    private val sha1TripleDesOid =
        byteArrayOf(0x06, 0x0A, 0x2A, 0x86.toByte(), 0x48, 0x86.toByte(), 0xF7.toByte(), 0x0D, 0x01, 0x0C, 0x01, 0x03)

    // DER prefix of the MacData DigestInfo for a SHA1 MAC: AlgorithmIdentifier(1.3.14.3.2.26, NULL)
    // followed by the 20-byte OCTET STRING header — must appear.
    private val sha1MacDigestInfoPrefix =
        byteArrayOf(0x30, 0x09, 0x06, 0x05, 0x2B, 0x0E, 0x03, 0x02, 0x1A, 0x05, 0x00, 0x04, 0x14)

    private fun ByteArray.containsSequence(needle: ByteArray): Boolean =
        (0..size - needle.size).any { offset -> needle.indices.all { this[offset + it] == needle[it] } }

    private fun assertLegacyEncoding(bytes: ByteArray, name: String) {
        assertFalse(bytes.containsSequence(pbes2Oid), "$name uses PBES2/AES encryption, unreadable on Android <= 9")
        assertTrue(bytes.containsSequence(sha1TripleDesOid), "$name is missing legacy SHA1/3DES PKCS#12 encryption")
        assertTrue(bytes.containsSequence(sha1MacDigestInfoPrefix), "$name is missing a SHA1 PKCS#12 MAC")
    }

    private fun assertLoadsAsPkcs12(bytes: ByteArray, name: String) {
        val password = TAK_BUNDLED_CERT_PASSWORD.toCharArray()
        val keyStore = KeyStore.getInstance("PKCS12")
        ByteArrayInputStream(bytes).use { keyStore.load(it, password) }
        val privateKeys = keyStore.aliases().toList().mapNotNull { keyStore.getKey(it, password) as? PrivateKey }
        assertTrue(privateKeys.isNotEmpty(), "$name has no private key entry")
    }

    @Test
    fun `server p12 uses legacy encoding and loads`() {
        val bytes = assertNotNull(TakCertLoader.getServerP12Bytes(), "server.p12 missing from classpath")
        assertLegacyEncoding(bytes, "server.p12")
        assertLoadsAsPkcs12(bytes, "server.p12")
    }

    @Test
    fun `client p12 uses legacy encoding and loads`() {
        val bytes = assertNotNull(TakCertLoader.getClientP12Bytes(), "client.p12 missing from classpath")
        assertLegacyEncoding(bytes, "client.p12")
        assertLoadsAsPkcs12(bytes, "client.p12")
    }

    @Test
    fun `server ssl context builds from bundled certs`() {
        assertNotNull(TakCertLoader.getServerSslContext(), "SSLContext could not be built from bundled certs")
    }
}
