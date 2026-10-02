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

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SecurityKeyBackupStoreImplTest {
    private lateinit var dataDir: File

    @BeforeTest
    fun setUp() {
        dataDir = Files.createTempDirectory("security-key-backup-store-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        dataDir.deleteRecursively()
    }

    @Test
    fun `save get and delete round trips on jvm`() {
        val store = SecurityKeyBackupStoreImpl(dataDir)

        store.save(nodeNum = 42, publicKeyBase64 = "pub", privateKeyBase64 = "priv", timestamp = 1_700_000_000L)

        val stored = store.get(42)
        assertEquals("pub", stored?.publicKeyBase64)
        assertEquals("priv", stored?.privateKeyBase64)
        assertEquals(1_700_000_000L, stored?.timestamp)

        store.delete(42)

        assertNull(store.get(42))
    }

    @Test
    fun `a keystore missing its master key is never overwritten`() {
        SecurityKeyBackupStoreImpl(dataDir).save(42, "pub", "priv", timestamp = 1L)
        val storeDir = File(dataDir, "security_keys")
        removeKeystoreEntry(storeDir, alias = "security_key_backup_master", password = "meshtastic-security-keys")
        val keystoreBefore = File(storeDir, "keystore.p12").readBytes()
        val entryBefore = File(storeDir, "42.enc").readBytes()

        val reopened = SecurityKeyBackupStoreImpl(dataDir)

        assertNull(reopened.get(42))
        assertFailsWith<IllegalStateException> { reopened.save(7, "pub7", "priv7", timestamp = 2L) }
        assertContentEquals(keystoreBefore, File(storeDir, "keystore.p12").readBytes())
        assertContentEquals(entryBefore, File(storeDir, "42.enc").readBytes())
    }
}
