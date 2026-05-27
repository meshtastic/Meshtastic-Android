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

import groovy.json.JsonOutput
import org.gradle.api.logging.Logger
import java.io.File
import java.net.URI
import java.security.MessageDigest

/** Transforms captured URLs into a Flathub-compliant flatpak-sources JSON file. */
internal class SourcesWriter(
    private val filesRoot: File,
    private val destPrefix: String,
    private val excludeSuffixes: Set<String>,
    private val generateMirrors: Boolean,
    private val repoUrls: List<String>,
    private val logger: Logger,
) {
    /**
     * Writes the Flatpak sources manifest from captured URLs.
     *
     * @param urls All URLs captured during the build.
     * @param output The file to write the JSON manifest to.
     */
    fun write(urls: List<String>, output: File) {
        val entries: List<Map<String, Any>> =
            urls
                .distinct()
                .filterNot { url ->
                    val tail = url.substringAfterLast('/')
                    excludeSuffixes.any { suffix -> tail.endsWith(suffix) }
                }
                .sorted()
                .mapNotNull { url -> buildEntry(url) }
        output.parentFile?.mkdirs()
        output.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(entries)))
        logger.lifecycle(
            "flatpak-sources: captured {} URLs, emitted {} sources to {}",
            urls.size,
            entries.size,
            output.absolutePath,
        )
    }

    private fun buildEntry(url: String): Map<String, Any>? {
        val cacheFile = CacheFileLocator.locate(filesRoot, url)
        val rel = cacheFile?.let { CacheFileLocator.relativePath(filesRoot, it) }
        if (cacheFile != null && rel != null) {
            return buildCachedEntry(url, cacheFile, rel)
        }
        // Fallback: download to compute SHA-256 (for artifacts not yet in local cache)
        return buildRemoteEntry(url)
    }

    private fun buildCachedEntry(url: String, cacheFile: File, rel: List<String>): Map<String, Any> {
        val group = rel[0]
        val artifact = rel[1]
        val version = rel[2]
        val onDiskFilename = rel.last()
        val groupPath = group.replace('.', '/')
        val entry =
            mutableMapOf<String, Any>(
                "type" to "file",
                "url" to url,
                "sha256" to sha256(cacheFile),
                "dest" to "$destPrefix/$groupPath/$artifact/$version",
                "dest-filename" to onDiskFilename,
            )
        if (generateMirrors) {
            MirrorGenerator.mirrorsFor(url).takeIf { it.isNotEmpty() }?.let { entry["mirror-urls"] = it }
        }
        return entry
    }

    private fun buildRemoteEntry(url: String): Map<String, Any>? {
        // Strip the repo base URL to get the Maven-relative path (group/artifact/version/file)
        val mavenPath =
            repoUrls.firstNotNullOfOrNull { repo ->
                if (url.startsWith(repo)) url.removePrefix(repo).trimStart('/') else null
            }

        val segments =
            if (mavenPath != null) {
                mavenPath.split('/').filter { it.isNotEmpty() }
            } else {
                // Fallback: use full URL path (may include repo prefix)
                URI(url).path.trimEnd('/').split('/').filter { it.isNotEmpty() }
            }

        if (segments.size < URL_MIN_SEGMENTS) {
            logger.info("flatpak-sources: cannot derive path for {} (skipped)", url)
            return null
        }
        val filename = segments.last()
        val version = segments[segments.size - 2]
        val artifact = segments[segments.size - URL_MIN_SEGMENTS]
        val groupSegments = segments.dropLast(URL_MIN_SEGMENTS)
        val groupPath = groupSegments.joinToString("/")
        val hash = downloadAndHash(url) ?: return null
        val entry =
            mutableMapOf<String, Any>(
                "type" to "file",
                "url" to url,
                "sha256" to hash,
                "dest" to "$destPrefix/$groupPath/$artifact/$version",
                "dest-filename" to filename,
            )
        if (generateMirrors) {
            MirrorGenerator.mirrorsFor(url).takeIf { it.isNotEmpty() }?.let { entry["mirror-urls"] = it }
        }
        return entry
    }

    private fun sha256(file: File): String = hashStream(file.inputStream())

    private fun downloadAndHash(url: String): String? = try {
        val conn = URI(url).toURL().openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = DOWNLOAD_CONNECT_TIMEOUT_MS
        conn.readTimeout = DOWNLOAD_READ_TIMEOUT_MS
        val hash = hashStream(conn.inputStream).also { conn.disconnect() }
        logger.lifecycle("flatpak-sources: downloaded {} (sha256:{})", url, hash.take(HASH_PREFIX_LEN))
        hash
    } catch (e: Exception) {
        logger.warn("flatpak-sources: failed to download {} — {}", url, e.message)
        null
    }

    private fun hashStream(stream: java.io.InputStream): String {
        val md = MessageDigest.getInstance("SHA-256")
        stream.use {
            val buf = ByteArray(BUFFER_SIZE)
            while (true) {
                val n = it.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }

    private companion object {
        private const val BUFFER_SIZE = 8192
        private const val DOWNLOAD_CONNECT_TIMEOUT_MS = 30_000
        private const val DOWNLOAD_READ_TIMEOUT_MS = 60_000
        private const val URL_MIN_SEGMENTS = 3
        private const val HASH_PREFIX_LEN = 12
    }
}
