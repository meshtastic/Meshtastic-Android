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

import co.touchlab.kermit.Logger
import java.io.File
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.atomic.AtomicBoolean

/*
 * Owner-only file helpers for the desktop data directory. On POSIX file systems they use `rwx------` for directories
 * and `rw-------` for files; elsewhere they fall back to `java.io.File` permission flags. None of them throw: a
 * failure to restrict permissions is logged once and never blocks startup.
 */

private val OWNER_DIR: Set<PosixFilePermission> = PosixFilePermissions.fromString("rwx------")
private val OWNER_FILE: Set<PosixFilePermission> = PosixFilePermissions.fromString("rw-------")
private val fallbackWarned = AtomicBoolean(false)

private fun posixSupported(): Boolean = "posix" in FileSystems.getDefault().supportedFileAttributeViews()

/**
 * Creates [dir] and any missing parents, each owner-only, then restricts [dir] itself to owner-only even when it
 * already existed. Existing parents are left untouched.
 */
fun createOwnerOnlyDirectories(dir: File) {
    createOwnerOnlyDirectories(dir, posixSupported(), tightenExisting = true)
}

/**
 * Creates [dir] and any missing parents, each owner-only, and leaves [dir] untouched when it already exists. For a
 * directory the user chose, such as an explicit `MESHTASTIC_DATA_DIR`.
 */
fun createMissingOwnerOnlyDirectories(dir: File) {
    createOwnerOnlyDirectories(dir, posixSupported(), tightenExisting = false)
}

internal fun createOwnerOnlyDirectories(dir: File, posix: Boolean, tightenExisting: Boolean = true) {
    val missing = generateSequence(dir.absoluteFile) { it.parentFile }.takeWhile { !it.exists() }.toList().asReversed()
    for (target in missing) {
        val created =
            try {
                if (posix) {
                    Files.createDirectory(target.toPath(), PosixFilePermissions.asFileAttribute(OWNER_DIR))
                } else {
                    Files.createDirectory(target.toPath())
                }
                true
            } catch (@Suppress("SwallowedException") e: IOException) {
                target.isDirectory
            }
        if (!created) break
        if (!posix) restrictToOwner(target, posix = false)
    }
    if (tightenExisting && dir.isDirectory) restrictToOwner(dir, posix)
}

/** Creates [file] owner-only when absent, or restricts it when it already exists. Its parent must exist. */
fun ensureOwnerOnlyFile(file: File) {
    ensureOwnerOnlyFile(file, posixSupported())
}

internal fun ensureOwnerOnlyFile(file: File, posix: Boolean) {
    if (!file.exists()) {
        try {
            if (posix) {
                Files.createFile(file.toPath(), PosixFilePermissions.asFileAttribute(OWNER_FILE))
            } else {
                Files.createFile(file.toPath())
            }
        } catch (@Suppress("SwallowedException") e: IOException) {
            // Raced with another creator or the parent is missing; the caller's own write reports real failures.
        }
    }
    if (file.exists()) restrictToOwner(file, posix)
}

/** Restricts an existing file or directory to owner-only access. */
fun restrictToOwner(file: File) {
    restrictToOwner(file, posixSupported())
}

internal fun restrictToOwner(file: File, posix: Boolean) {
    val restricted =
        try {
            if (posix) {
                Files.setPosixFilePermissions(file.toPath(), if (file.isDirectory) OWNER_DIR else OWNER_FILE)
                true
            } else {
                restrictWithFileFlags(file)
            }
        } catch (@Suppress("SwallowedException") e: IOException) {
            false
        } catch (@Suppress("SwallowedException") e: UnsupportedOperationException) {
            false
        } catch (@Suppress("SwallowedException") e: SecurityException) {
            false
        }
    if (!restricted && fallbackWarned.compareAndSet(false, true)) {
        Logger.w { "Could not restrict desktop data permissions to the current user" }
    }
}

private fun restrictWithFileFlags(file: File): Boolean {
    val isDir = file.isDirectory
    var ok = file.setReadable(false, false) && file.setReadable(true, true)
    ok = file.setWritable(false, false) && file.setWritable(true, true) && ok
    ok = file.setExecutable(false, false) && (!isDir || file.setExecutable(true, true)) && ok
    return ok
}
