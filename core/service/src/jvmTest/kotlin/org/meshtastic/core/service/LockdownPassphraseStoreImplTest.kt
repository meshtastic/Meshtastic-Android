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
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LockdownPassphraseStoreImplTest {
    private lateinit var tempHome: java.nio.file.Path
    private lateinit var originalUserHome: String

    @BeforeTest
    fun setUp() {
        originalUserHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("lockdown-passphrase-store-test")
        System.setProperty("user.home", tempHome.toString())
    }

    @AfterTest
    fun tearDown() {
        System.setProperty("user.home", originalUserHome)
        File(tempHome.toString()).deleteRecursively()
    }

    @Test
    fun `save get and clear passphrase round trips on jvm`() {
        val store = LockdownPassphraseStoreImpl()

        store.savePassphrase(deviceAddress = "AA:BB:CC:DD", passphrase = "secret", boots = 10, hours = 24)

        val stored = store.getPassphrase("AA:BB:CC:DD")
        assertEquals("secret", stored?.passphrase)
        assertEquals(10, stored?.boots)
        assertEquals(24, stored?.hours)

        store.clearPassphrase("AA:BB:CC:DD")

        assertNull(store.getPassphrase("AA:BB:CC:DD"))
    }
}