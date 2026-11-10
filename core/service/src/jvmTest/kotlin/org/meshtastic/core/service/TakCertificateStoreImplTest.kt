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
package org.meshtastic.core.service

import org.meshtastic.core.repository.TakCertificateMaterial
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TakCertificateStoreImplTest {
    private lateinit var dataDir: File

    @BeforeTest
    fun setUp() {
        dataDir = Files.createTempDirectory("tak-certificate-store-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        dataDir.deleteRecursively()
    }

    private fun material(seed: Int) = TakCertificateMaterial(
        serverP12 = ByteArray(64) { (it + seed).toByte() },
        clientP12 = ByteArray(32) { (it * 2 + seed).toByte() },
        truststoreP12 = ByteArray(16) { (it * 3 + seed).toByte() },
        password = "pw-$seed",
    )

    @Test
    fun `save load and clear round trips on jvm`() {
        val store = TakCertificateStoreImpl(dataDir)
        assertNull(store.load())

        val saved = material(1)
        store.save(saved)

        val loaded = assertNotNull(TakCertificateStoreImpl(dataDir).load())
        assertEquals(saved.password, loaded.password)
        assertContentEquals(saved.serverP12, loaded.serverP12)
        assertContentEquals(saved.clientP12, loaded.clientP12)
        assertContentEquals(saved.truststoreP12, loaded.truststoreP12)

        store.clear()
        assertNull(store.load())
    }

    @Test
    fun `save replaces previous material`() {
        val store = TakCertificateStoreImpl(dataDir)
        store.save(material(1))
        store.save(material(2))

        val loaded = assertNotNull(store.load())
        assertEquals("pw-2", loaded.password)
        assertContentEquals(material(2).serverP12, loaded.serverP12)
        assertContentEquals(material(2).clientP12, loaded.clientP12)
        assertContentEquals(material(2).truststoreP12, loaded.truststoreP12)
        assertEquals(listOf("material.enc"), File(dataDir, "tak_certs").list()!!.filter { it.endsWith(".enc") })
    }

    @Test
    fun `truncated or garbage material reads as empty`() {
        val store = TakCertificateStoreImpl(dataDir)
        store.save(material(3))
        val file = File(dataDir, "tak_certs/material.enc")

        file.writeBytes(file.readBytes().copyOf(file.length().toInt() / 2))
        assertNull(TakCertificateStoreImpl(dataDir).load(), "truncated ciphertext must read as empty")

        file.writeBytes(ByteArray(40) { 0x41 })
        assertNull(TakCertificateStoreImpl(dataDir).load(), "garbage must read as empty")

        store.save(material(4))
        assertEquals("pw-4", TakCertificateStoreImpl(dataDir).load()?.password)
    }

    @Test
    fun `material is not stored in plaintext`() {
        val store = TakCertificateStoreImpl(dataDir)
        val saved = material(7)
        store.save(saved)

        val onDisk = File(dataDir, "tak_certs/material.enc").readBytes()
        assertFalse(onDisk.containsSequence(saved.serverP12), "server.p12 bytes must be encrypted at rest")
        assertFalse(onDisk.containsSequence(saved.clientP12), "client.p12 bytes must be encrypted at rest")
        assertFalse(onDisk.containsSequence(saved.truststoreP12), "truststore bytes must be encrypted at rest")
        assertFalse(onDisk.containsSequence(saved.password.encodeToByteArray()), "password must be encrypted at rest")
    }

    private fun ByteArray.containsSequence(needle: ByteArray): Boolean =
        (0..size - needle.size).any { offset -> needle.indices.all { this[offset + it] == needle[it] } }
}
