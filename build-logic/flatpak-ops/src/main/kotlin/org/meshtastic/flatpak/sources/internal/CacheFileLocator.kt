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
package org.meshtastic.flatpak.sources.internal

import java.io.File
import java.net.URI

/**
 * Locates cached artifact files in Gradle's `caches/modules-2/files-2.1` directory based on their download URL.
 *
 * The last three URL path segments are artifact/version/filename (Maven 2 spec). Gradle's files-2.1 layout is
 * `<group-with-dots>/<artifact>/<version>/<content-sha1>/<filename>`. The (artifact, version, filename) triple is NOT
 * unique across groups, so the group MUST be derived from the URL path. We probe every suffix of the path's leading
 * segments against the cache layout — the longest match wins (strips arbitrary repo prefixes like `/maven2/`,
 * `/dl/android/maven2/`, `/m2/` without hardcoding them).
 */
internal object CacheFileLocator {

    private const val URL_TRAILING_SEGMENTS = 3

    /**
     * Resolves the on-disk cache file for a given URL, or null if not found.
     *
     * @param filesRoot The `files-2.1` cache directory.
     * @param url The external resource URL that was downloaded.
     * @return The cached [File], or null if the URL doesn't map to a cached Maven artifact.
     */
    fun locate(filesRoot: File, url: String): File? {
        val path = URI(url).path.trimEnd('/').split('/').filter { it.isNotEmpty() }
        val tail = path.takeLast(URL_TRAILING_SEGMENTS)
        if (!filesRoot.isDirectory || tail.size < URL_TRAILING_SEGMENTS) return null
        val (artifact, version, filename) = tail
        val groupCandidates = path.dropLast(URL_TRAILING_SEGMENTS)
        return groupCandidates.indices.firstNotNullOfOrNull { start ->
            val groupDir = groupCandidates.drop(start).joinToString(".")
            File(filesRoot, "$groupDir/$artifact/$version")
                .takeIf(File::isDirectory)
                ?.listFiles { f -> f.isDirectory }
                ?.map { shaDir -> File(shaDir, filename) }
                ?.firstOrNull { it.isFile }
        }
    }

    /**
     * Extracts the relative cache path segments from [filesRoot] for a given [cacheFile].
     *
     * @return A list of path segments: [group, artifact, version, sha1, filename], or null if the path has fewer than
     *   [CACHE_PATH_SEGMENTS] components.
     */
    fun relativePath(filesRoot: File, cacheFile: File): List<String>? {
        val rel = cacheFile.relativeTo(filesRoot).path.replace('\\', '/').split('/')
        return if (rel.size >= CACHE_PATH_SEGMENTS) rel else null
    }

    private const val CACHE_PATH_SEGMENTS = 5
}
