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
package org.meshtastic.core.data.radio

import org.meshtastic.core.repository.TransportDisconnectReason
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.io.path.createFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SerialFailureReasonTest {
    private val dev = Files.createTempDirectory("serial-failure")

    @AfterTest
    fun cleanUp() {
        dev.toFile().deleteRecursively()
    }

    private fun node(name: String, permissions: String) = dev.resolve(name).createFile().also {
        Files.setPosixFilePermissions(it, PosixFilePermissions.fromString(permissions))
    }

    @Test
    fun `a node this account cannot open is a permission problem on Linux`() {
        val locked = node("ttyACM1", "r--------")

        assertEquals(
            TransportDisconnectReason.SerialPermissionDenied,
            serialFailureReason(locked.toString(), isLinux = true),
        )
    }

    @Test
    fun `an openable node or a missing one is not a permission problem`() {
        val open = node("ttyACM0", "rw-------")

        assertNull(serialFailureReason(open.toString(), isLinux = true))
        assertNull(serialFailureReason(dev.resolve("ttyACM9").toString(), isLinux = true))
    }

    @Test
    fun `other hosts get no Linux group hint`() {
        val locked = node("ttyACM1", "---------")

        assertNull(serialFailureReason(locked.toString(), isLinux = false))
    }
}
