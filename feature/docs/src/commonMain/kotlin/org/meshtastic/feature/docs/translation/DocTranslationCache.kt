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
package org.meshtastic.feature.docs.translation

import okio.FileSystem
import okio.Path
import okio.buffer
import org.koin.core.annotation.Single

/**
 * File-based cache for ML Kit translated markdown pages.
 *
 * Cache key: `{pageId}#{locale}#{md5(sourceContent)}` When the English source changes, the md5 changes and the old
 * cache entry becomes stale. LRU eviction at [maxCacheSizeBytes] (default 50MB).
 */
@Single
class DocTranslationCache(
    private val cacheDir: Path,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val maxCacheSizeBytes: Long = MAX_CACHE_SIZE_BYTES,
) {
    companion object {
        const val MAX_CACHE_SIZE_BYTES = 50L * 1024 * 1024 // 50 MB
        private const val CACHE_SUBDIR = "docs_translation_cache"
        private const val EVICTION_TARGET_PERCENT = 75
        private const val PERCENT_DIVISOR = 100
    }

    private val cacheRoot: Path
        get() = cacheDir / CACHE_SUBDIR

    /** Get a cached translation, or null if not cached or stale. */
    fun get(pageId: String, locale: String, sourceHash: String): String? {
        val file = cacheFile(pageId, locale, sourceHash)
        return try {
            if (fileSystem.exists(file)) {
                val bufferedSource = fileSystem.source(file).buffer()
                val content = bufferedSource.readUtf8()
                bufferedSource.close()
                content
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Store a translated page in the cache. Evicts old entries if over size limit. */
    fun put(pageId: String, locale: String, sourceHash: String, translatedMarkdown: String) {
        try {
            fileSystem.createDirectories(cacheRoot)
            val file = cacheFile(pageId, locale, sourceHash)
            val bufferedSink = fileSystem.sink(file).buffer()
            bufferedSink.writeUtf8(translatedMarkdown)
            bufferedSink.close()
            evictIfNeeded()
        } catch (_: Exception) {
            // Cache write failure is non-fatal
        }
    }

    /** Remove all cached translations. */
    fun clear() {
        try {
            if (fileSystem.exists(cacheRoot)) {
                fileSystem.deleteRecursively(cacheRoot)
            }
        } catch (_: Exception) {
            // Best effort
        }
    }

    /** Total bytes used by the cache. */
    fun sizeBytes(): Long = try {
        if (!fileSystem.exists(cacheRoot)) return 0L
        fileSystem
            .listRecursively(cacheRoot)
            .filter { fileSystem.metadata(it).isRegularFile }
            .sumOf { fileSystem.metadata(it).size ?: 0L }
    } catch (_: Exception) {
        0L
    }

    private fun cacheFile(pageId: String, locale: String, sourceHash: String): Path {
        val safeKey = "${pageId}_${locale}_$sourceHash".replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return cacheRoot / "$safeKey.md"
    }

    private fun evictIfNeeded() {
        if (sizeBytes() <= maxCacheSizeBytes) return

        try {
            // LRU eviction: delete oldest files first (by last modified time)
            val files =
                fileSystem
                    .listRecursively(cacheRoot)
                    .filter { fileSystem.metadata(it).isRegularFile }
                    .sortedBy { fileSystem.metadata(it).lastModifiedAtMillis ?: 0L }
                    .toList()

            var currentSize = sizeBytes()
            for (file in files) {
                if (currentSize <= maxCacheSizeBytes * EVICTION_TARGET_PERCENT / PERCENT_DIVISOR) break
                val fileSize = fileSystem.metadata(file).size ?: 0L
                fileSystem.delete(file)
                currentSize -= fileSize
            }
        } catch (_: Exception) {
            // Best effort eviction
        }
    }
}

/** Simple MD5 hash for cache key generation. Uses Okio's built-in hashing. */
fun md5Hash(content: String): String {
    val buffer = okio.Buffer()
    buffer.writeUtf8(content)
    return buffer.md5().hex()
}
