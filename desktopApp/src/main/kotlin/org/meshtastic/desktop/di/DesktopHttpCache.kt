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
package org.meshtastic.desktop.di

import org.meshtastic.core.database.desktopDataDir
import java.io.File

/** Size bound for the desktop HTTP cache, the same as the OkHttp cache behind Android's client. */
internal const val HTTP_CACHE_MAX_BYTES = 10L * 1024L * 1024L

/** The desktop HTTP cache directory, trimmed to [HTTP_CACHE_MAX_BYTES] and created if missing. */
internal fun preparedHttpCacheDir(): File =
    File(desktopDataDir(), "http_cache").also { trimDirectoryToBudget(it, HTTP_CACHE_MAX_BYTES) }

/**
 * Keeps the most recently written files in [directory] that fit in [maxBytes] and deletes the rest. Ktor's file cache
 * storage has no size bound of its own, so the client trims it once before it starts using the directory.
 */
internal fun trimDirectoryToBudget(directory: File, maxBytes: Long) {
    directory.mkdirs()
    var kept = 0L
    directory
        .walkTopDown()
        .filter { it.isFile }
        .sortedByDescending { it.lastModified() }
        .forEach { file ->
            val size = file.length()
            if (kept + size <= maxBytes) kept += size else file.delete()
        }
}
