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
package org.meshtastic.feature.map.mapcompose.tile

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.FileSystem
import okio.Path

/**
 * A size-bounded LRU disk cache for map tiles, keyed by `sourceId/zoom/row/col`. Backed by Okio so tests inject a
 * `FakeFileSystem`; recency is tracked in memory (rebuilt from a directory walk on first use) rather than via a
 * journal file, which keeps corruption handling trivial — any unreadable entry is treated as a miss and deleted.
 */
class TileDiskCache(
    private val fileSystem: FileSystem,
    private val root: Path,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) {
    private val mutex = Mutex()

    /** Keys in least-recently-used-first order, with per-entry sizes; lazily seeded from disk. */
    private val entries = LinkedHashMap<String, Long>()
    private var totalBytes = 0L
    private var scanned = false

    suspend fun read(sourceId: String, zoom: Int, row: Int, col: Int): ByteArray? =
        mutex.withLock {
            ensureScanned()
            val key = key(sourceId, zoom, row, col)
            val path = root / key
            val size = entries.remove(key) ?: return null
            runCatching { fileSystem.read(path) { readByteArray() } }
                .onFailure {
                    totalBytes -= size
                    runCatching { fileSystem.delete(path) }
                    return null
                }
                .map { bytes ->
                    entries[key] = size // re-insert as most recently used
                    bytes
                }
                .getOrNull()
        }

    suspend fun write(sourceId: String, zoom: Int, row: Int, col: Int, bytes: ByteArray) {
        mutex.withLock {
            ensureScanned()
            val key = key(sourceId, zoom, row, col)
            val path = root / key
            runCatching {
                path.parent?.let { fileSystem.createDirectories(it) }
                val tmp = root / "$key.tmp"
                fileSystem.write(tmp) { write(bytes) }
                fileSystem.atomicMove(tmp, path)
            }
                .onFailure {
                    return
                }
            entries.remove(key)?.let { totalBytes -= it }
            entries[key] = bytes.size.toLong()
            totalBytes += bytes.size
            evictIfNeeded()
        }
    }

    private fun key(sourceId: String, zoom: Int, row: Int, col: Int): String = "$sourceId/$zoom/$row/$col"

    private fun ensureScanned() {
        if (scanned) return
        scanned = true
        runCatching {
            if (!fileSystem.exists(root)) return
            fileSystem.listRecursively(root).forEach { path ->
                val meta = fileSystem.metadataOrNull(path) ?: return@forEach
                if (meta.isRegularFile && !path.name.endsWith(".tmp")) {
                    val key = path.toString().removePrefix(root.toString()).trimStart('/', '\\')
                    val size = meta.size ?: 0L
                    entries[key] = size
                    totalBytes += size
                }
            }
        }
    }

    private fun evictIfNeeded() {
        while (totalBytes > maxBytes && entries.isNotEmpty()) {
            val (oldestKey, size) = entries.entries.first()
            entries.remove(oldestKey)
            totalBytes -= size
            runCatching { fileSystem.delete(root / oldestKey) }
        }
    }

    companion object {
        const val DEFAULT_MAX_BYTES: Long = 256L * 1024 * 1024
    }
}
