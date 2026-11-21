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
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** POSIX assertions return early on file systems without POSIX permissions. */
class DesktopStorePermissionsTest {
    private lateinit var dataDir: File

    private val posix = "posix" in FileSystems.getDefault().supportedFileAttributeViews()

    @BeforeTest
    fun setUp() {
        dataDir = Files.createTempDirectory("desktop-store-permissions-test").toFile()
        if (posix) Files.setPosixFilePermissions(dataDir.toPath(), PosixFilePermissions.fromString("rwxr-xr-x"))
    }

    @AfterTest
    fun tearDown() {
        dataDir.deleteRecursively()
    }

    private fun mode(file: File): String = PosixFilePermissions.toString(Files.getPosixFilePermissions(file.toPath()))

    private fun File.assertNoGroupOrOtherAccess() {
        val groupOrOther =
            Files.getPosixFilePermissions(toPath()).filter {
                it.name.startsWith("GROUP") || it.name.startsWith("OTHERS")
            }
        assertTrue(groupOrOther.isEmpty(), "$name is accessible to group or others: $groupOrOther")
    }

    @Test
    fun `tak certificate store writes only owner accessible files and directories`() {
        if (!posix) return
        val store = TakCertificateStoreImpl(dataDir)

        store.save(
            TakCertificateMaterial(
                serverP12 = ByteArray(8),
                clientP12 = ByteArray(8),
                truststoreP12 = ByteArray(8),
                password = "pw",
            ),
        )

        val storeDir = File(dataDir, "tak_certs")
        storeDir.assertNoGroupOrOtherAccess()
        storeDir.listFiles().orEmpty().also { assertTrue(it.isNotEmpty()) }.forEach { it.assertNoGroupOrOtherAccess() }
        assertEquals(setOf("keystore.p12", "material.enc"), storeDir.list().orEmpty().toSet())
        assertEquals("rwxr-xr-x", mode(dataDir))
    }

    @Test
    fun `a key backup written over a world readable file becomes owner only`() {
        if (!posix) return
        val storeDir = File(dataDir, "security_keys")
        SecurityKeyBackupStoreImpl(dataDir).save(7, "pub", "priv", 1L)
        val entry = File(storeDir, "7.enc")
        Files.setPosixFilePermissions(entry.toPath(), PosixFilePermissions.fromString("rw-r--r--"))
        Files.setPosixFilePermissions(storeDir.toPath(), PosixFilePermissions.fromString("rwxr-xr-x"))

        SecurityKeyBackupStoreImpl(dataDir).save(7, "pub2", "priv2", 2L)

        assertEquals("rw-------", mode(entry))
        assertEquals("rwx------", mode(storeDir))
    }
}
