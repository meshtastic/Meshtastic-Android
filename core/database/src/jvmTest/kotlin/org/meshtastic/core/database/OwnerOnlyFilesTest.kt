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
package org.meshtastic.core.database

import java.io.File
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/** POSIX assertions return early on file systems without POSIX permissions. */
class OwnerOnlyFilesTest {
    private lateinit var root: File

    private val posix = "posix" in FileSystems.getDefault().supportedFileAttributeViews()

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("owner-only-files-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun mode(file: File): String = PosixFilePermissions.toString(Files.getPosixFilePermissions(file.toPath()))

    private fun setMode(file: File, mode: String) =
        Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString(mode))

    @Test
    fun `created directories are owner only and an existing parent is left alone`() {
        if (!posix) return
        setMode(root, "rwxr-xr-x")

        val leaf = File(root, "a/b")
        createOwnerOnlyDirectories(leaf)

        assertEquals("rwx------", mode(leaf))
        assertEquals("rwx------", mode(File(root, "a")))
        assertEquals("rwxr-xr-x", mode(root))
    }

    @Test
    fun `an existing world readable directory is tightened`() {
        if (!posix) return
        val dir = File(root, "existing").apply { mkdir() }
        setMode(dir, "rwxr-xr-x")

        createOwnerOnlyDirectories(dir)

        assertEquals("rwx------", mode(dir))
    }

    @Test
    fun `an owner only file is created and an existing one is tightened`() {
        if (!posix) return
        val created = File(root, "new.bin")
        ensureOwnerOnlyFile(created)
        assertEquals("rw-------", mode(created))

        val existing = File(root, "old.bin").apply { writeText("x") }
        setMode(existing, "rw-r--r--")
        ensureOwnerOnlyFile(existing)
        assertEquals("rw-------", mode(existing))
        assertEquals("x", existing.readText())
    }

    @Test
    fun `the file flag fallback leaves a file and a directory owner only`() {
        if (!posix) return
        val file = File(root, "f.bin").apply { writeText("x") }
        val dir = File(root, "d").apply { mkdir() }
        setMode(file, "rw-rw-rw-")
        setMode(dir, "rwxrwxrwx")

        restrictToOwner(file, posix = false)
        restrictToOwner(dir, posix = false)

        assertEquals("rw-------", mode(file))
        assertEquals("rwx------", mode(dir))
    }

    @Test
    fun `an existing explicit data root keeps its mode while the database file is owner only`() {
        if (!posix) return
        setMode(root, "rwxrwx---")

        val dbFile = prepareDatabaseFile(root.path, "meshtastic")
        val dataDir = prepareDataDir(root.path, home = "/unused")

        assertEquals("rwxrwx---", mode(root))
        assertEquals(root.path, dataDir)
        assertEquals("rw-------", mode(dbFile))
    }

    @Test
    fun `a missing explicit data root is created owner only`() {
        if (!posix) return
        val missing = File(root, "mesh/data")

        prepareDatabaseFile(missing.path, "meshtastic")

        assertEquals("rwx------", mode(missing))
    }

    @Test
    fun `the default data root is tightened when it already exists`() {
        if (!posix) return
        val home = File(root, "home").apply { mkdir() }
        val dataRoot = File(home, ".meshtastic").apply { mkdir() }
        setMode(dataRoot, "rwxr-xr-x")

        prepareDataDir(override = null, home = home.path)

        assertEquals("rwx------", mode(dataRoot))
    }
}
