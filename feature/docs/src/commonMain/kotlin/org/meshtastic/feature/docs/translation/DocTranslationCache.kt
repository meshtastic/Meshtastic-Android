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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okio.FileSystem
import okio.IOException
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer

/**
 * File-based cache for ML Kit translated markdown pages.
 *
 * Cache key: `{pageId}#{locale}#{md5(sourceContent)}` When the English source changes, the md5 changes and the old
 * cache entry becomes stale. Eviction by oldest-access at [maxCacheSizeBytes] (default 50MB).
 */
class DocTranslationCache(
    private val cacheDir: Path,
    private val fileSystem: FileSystem,
    private val maxCacheSizeBytes: Long = MAX_CACHE_SIZE_BYTES,
) {
    companion object {
        const val MAX_CACHE_SIZE_BYTES = 50L * 1024 * 1024 // 50 MB
        private const val CACHE_SUBDIR = "docs_translation_cache"
        private const val EVICTION_TARGET_PERCENT = 75
        private const val PERCENT_DIVISOR = 100
        private const val TAG = "DocTranslationCache"
    }

    private val mutex = Mutex()

    private val cacheRoot: Path
        get() = cacheDir / CACHE_SUBDIR

    /** Get a cached translation, or null if not cached or stale. */
    suspend fun get(pageId: String, locale: String, sourceHash: String): String? = mutex.withLock {
        val file = cacheFile(pageId, locale, sourceHash)
        try {
            if (fileSystem.exists(file)) {
                val bufferedSource = fileSystem.source(file).buffer()
                val content = bufferedSource.readUtf8()
                bufferedSource.close()
                // Touch file to update access time for eviction ordering
                touchFile(file)
                content
            } else {
                null
            }
        } catch (e: IOException) {
            Logger.w(tag = TAG) { "Cache read failed for $pageId/$locale: ${e.message}" }
            null
        }
    }

    /** Store a translated page in the cache. Evicts old entries if over size limit. */
    suspend fun put(pageId: String, locale: String, sourceHash: String, translatedMarkdown: String) = mutex.withLock {
        try {
            fileSystem.createDirectories(cacheRoot)
            val file = cacheFile(pageId, locale, sourceHash)
            // Write to temp file then move for atomicity
            val tmpFile = cacheRoot / "${file.name}.tmp"
            val bufferedSink = fileSystem.sink(tmpFile).buffer()
            bufferedSink.writeUtf8(translatedMarkdown)
            bufferedSink.close()
            fileSystem.atomicMove(tmpFile, file)
            evictIfNeeded()
        } catch (e: IOException) {
            Logger.w(tag = TAG) { "Cache write failed for $pageId/$locale: ${e.message}" }
        }
    }

    /** Remove all cached translations. */
    suspend fun clear() = mutex.withLock {
        try {
            if (fileSystem.exists(cacheRoot)) {
                fileSystem.deleteRecursively(cacheRoot)
            }
        } catch (e: IOException) {
            Logger.w(tag = TAG) { "Cache clear failed: ${e.message}" }
        }
    }

    /** Total bytes used by the cache. */
    suspend fun sizeBytes(): Long = mutex.withLock { sizeBytesInternal() }

    private fun sizeBytesInternal(): Long = try {
        if (!fileSystem.exists(cacheRoot)) return 0L
        fileSystem
            .listRecursively(cacheRoot)
            .filter { fileSystem.metadata(it).isRegularFile }
            .filter { it.name.endsWith(".md") }
            .sumOf { fileSystem.metadata(it).size ?: 0L }
    } catch (e: IOException) {
        Logger.w(tag = TAG) { "Cache size calculation failed: ${e.message}" }
        0L
    }

    private fun touchFile(file: Path) {
        // Write a tiny sidecar file to track last access time for eviction ordering.
        // The sidecar's own mtime serves as the access timestamp.
        try {
            val accessFile = "$file.access".toPath()
            val sink = fileSystem.sink(accessFile).buffer()
            sink.writeUtf8("1")
            sink.close()
        } catch (_: IOException) {
            // Non-fatal: eviction order may be slightly off
        }
    }

    private fun cacheFile(pageId: String, locale: String, sourceHash: String): Path {
        val safeKey = "${pageId}_${locale}_$sourceHash".replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return cacheRoot / "$safeKey.md"
    }

    private fun evictIfNeeded() {
        if (sizeBytesInternal() <= maxCacheSizeBytes) return

        try {
            val files =
                fileSystem
                    .listRecursively(cacheRoot)
                    .filter { fileSystem.metadata(it).isRegularFile }
                    .filter { it.name.endsWith(".md") }
                    .sortedBy { file ->
                        // Use sidecar access file mtime if available, else cache file mtime
                        val accessFile = "$file.access".toPath()
                        if (fileSystem.exists(accessFile)) {
                            fileSystem.metadata(accessFile).lastModifiedAtMillis ?: 0L
                        } else {
                            fileSystem.metadata(file).lastModifiedAtMillis ?: 0L
                        }
                    }
                    .toList()

            var currentSize = sizeBytesInternal()
            for (file in files) {
                if (currentSize <= maxCacheSizeBytes * EVICTION_TARGET_PERCENT / PERCENT_DIVISOR) break
                val fileSize = fileSystem.metadata(file).size ?: 0L
                fileSystem.delete(file)
                // Also delete sidecar access file
                val accessFile = "$file.access".toPath()
                try {
                    fileSystem.delete(accessFile)
                } catch (_: IOException) {
                    /* ignore */
                }
                currentSize -= fileSize
            }
        } catch (e: IOException) {
            Logger.w(tag = TAG) { "Cache eviction failed: ${e.message}" }
        }
    }
}

/** Simple MD5 hash for cache key generation. Uses Okio's built-in hashing. */
fun md5Hash(content: String): String {
    val buffer = okio.Buffer()
    buffer.writeUtf8(content)
    return buffer.md5().hex()
}
