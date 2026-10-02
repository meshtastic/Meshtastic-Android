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

class LockdownPassphraseStoreImplTest {
    private lateinit var dataDir: File

    @BeforeTest
    fun setUp() {
        dataDir = Files.createTempDirectory("lockdown-passphrase-store-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        dataDir.deleteRecursively()
    }

    @Test
    fun `save get and clear passphrase round trips on jvm`() {
        val store = LockdownPassphraseStoreImpl(dataDir)

        store.savePassphrase(deviceAddress = "AA:BB:CC:DD", passphrase = "secret", boots = 10, hours = 24)

        val stored = store.getPassphrase("AA:BB:CC:DD")
        assertEquals("secret", stored?.passphrase)
        assertEquals(10, stored?.boots)
        assertEquals(24, stored?.hours)

        store.clearPassphrase("AA:BB:CC:DD")

        assertNull(store.getPassphrase("AA:BB:CC:DD"))
    }

    @Test
    fun `a keystore missing its master key is never overwritten`() {
        LockdownPassphraseStoreImpl(dataDir).savePassphrase("AA:BB:CC:DD", "secret", boots = 10, hours = 24)
        val storeDir = File(dataDir, "lockdown")
        removeKeystoreEntry(storeDir, alias = "lockdown_master", password = "meshtastic-lockdown")
        val keystoreBefore = File(storeDir, "keystore.p12").readBytes()
        val entryBefore = File(storeDir, "AA_BB_CC_DD.enc").readBytes()

        val reopened = LockdownPassphraseStoreImpl(dataDir)

        assertNull(reopened.getPassphrase("AA:BB:CC:DD"))
        assertFailsWith<IllegalStateException> { reopened.savePassphrase("EE:FF", "other", boots = 1, hours = 1) }
        assertContentEquals(keystoreBefore, File(storeDir, "keystore.p12").readBytes())
        assertContentEquals(entryBefore, File(storeDir, "AA_BB_CC_DD.enc").readBytes())
    }
}
