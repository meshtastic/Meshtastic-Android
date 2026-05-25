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
package org.meshtastic.flatpakops

import groovy.json.JsonOutput
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.BuildOperationListenerManager
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.internal.resource.ExternalResourceReadBuildOperationType
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Captures every external resource URL Gradle reads via the internal BuildOperationListener API and emits a
 * Flathub-compliant flatpak-sources.json at build finish.
 *
 * No heuristics: URL is authoritative (taken straight from the build op), the on-disk file is found via Gradle's
 * documented files-2.1 layout, and SHA-256 is computed from that exact file.
 *
 * Internal APIs touched (acceptable trade-off; same path flatpak-gradle-generator uses):
 * - org.gradle.internal.operations.BuildOperationListener / BuildOperationListenerManager
 * - org.gradle.internal.resource.ExternalResourceReadBuildOperationType
 * - org.gradle.api.internal.project.ProjectInternal (for .services)
 */
class FlatpakOpsPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        require(target == target.rootProject) { "meshtastic.flatpak-ops must be applied to the root project" }

        val capturedUrls: MutableSet<String> = ConcurrentHashMap.newKeySet()
        val manager: BuildOperationListenerManager =
            (target as ProjectInternal).services.get(BuildOperationListenerManager::class.java)

        val listener = OpListener(capturedUrls)
        manager.addListener(listener)

        val outputProvider = target.layout.buildDirectory.file("flatpak-ops-sources.json")

        target.tasks.register("captureFlatpakSources") {
            group = "flatpak"
            description = "Emit flatpak-sources.json from URLs captured via BuildOperationListener."
            outputs.upToDateWhen { false }
            val proj = target
            val urlsRef = capturedUrls
            val outFile = outputProvider
            val mgr = manager
            val lst = listener
            doLast {
                writeSources(proj, urlsRef.toList(), outFile.get().asFile)
                runCatching { mgr.removeListener(lst) }
            }
        }
    }

    private class OpListener(private val urls: MutableSet<String>) : BuildOperationListener {
        override fun started(op: BuildOperationDescriptor, e: OperationStartEvent) = Unit

        override fun progress(id: OperationIdentifier, e: OperationProgressEvent) = Unit

        override fun finished(op: BuildOperationDescriptor, e: OperationFinishEvent) {
            val details = op.details as? ExternalResourceReadBuildOperationType.Details ?: return
            if (e.failure != null) return
            urls.add(details.location)
        }
    }

    private fun writeSources(project: Project, urls: List<String>, output: File) {
        val filesRoot = File(project.gradle.gradleUserHomeDir, "caches/modules-2/files-2.1")
        val entries: List<Map<String, Any>> =
            urls.distinct().sorted().mapNotNull { url ->
                val cacheFile = locateCacheFile(filesRoot, url)
                if (cacheFile == null) {
                    project.logger.info("flatpak-ops: no cache file for {} (skipped)", url)
                    return@mapNotNull null
                }
                val rel = cacheFile.relativeTo(filesRoot).path.replace('\\', '/').split('/')
                if (rel.size < CACHE_PATH_SEGMENTS) return@mapNotNull null
                val group = rel[0]
                val artifact = rel[1]
                val version = rel[2]
                val onDiskFilename = rel.last()
                val groupPath = group.replace('.', '/')
                mapOf(
                    "type" to "file",
                    "url" to url,
                    "sha256" to sha256(cacheFile),
                    "dest" to "offline-repository/$groupPath/$artifact/$version",
                    "dest-filename" to onDiskFilename,
                )
            }
        output.parentFile?.mkdirs()
        output.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(entries)))
        project.logger.lifecycle(
            "flatpak-ops: captured {} URLs, emitted {} sources to {}",
            urls.size,
            entries.size,
            output.absolutePath,
        )
    }

    /**
     * Last three URL path segments are artifact/version/filename (Maven 2 spec; present in every Maven URL). Gradle's
     * files-2.1 layout is group/artifact/version/content-sha1/filename. The (artifact, version, filename) triple is
     * globally unique, so probing files-2.1 / * / artifact / version / * / filename yields exactly one match. The group
     * is then read from the cache directory itself, not reconstructed.
     */
    private fun locateCacheFile(filesRoot: File, url: String): File? {
        val path = URI(url).path.trimEnd('/').split('/').filter { it.isNotEmpty() }
        val tail = path.takeLast(URL_TRAILING_SEGMENTS)
        if (!filesRoot.isDirectory || tail.size < URL_TRAILING_SEGMENTS) return null
        val (artifact, version, filename) = tail
        val groupDirs = filesRoot.listFiles { f -> f.isDirectory }.orEmpty()
        return groupDirs.firstNotNullOfOrNull { groupDir ->
            File(groupDir, "$artifact/$version")
                .takeIf(File::isDirectory)
                ?.listFiles { f -> f.isDirectory }
                ?.map { shaDir -> File(shaDir, filename) }
                ?.firstOrNull { it.isFile }
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buf = ByteArray(BUFFER_SIZE)
            while (true) {
                val n = stream.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return hex(md.digest())
    }

    private fun hex(bytes: ByteArray): String {
        val digits = "0123456789abcdef"
        val chars = CharArray(bytes.size * HEX_CHARS_PER_BYTE)
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and BYTE_MASK
            chars[i * HEX_CHARS_PER_BYTE] = digits[v ushr NIBBLE_BITS]
            chars[i * HEX_CHARS_PER_BYTE + 1] = digits[v and NIBBLE_MASK]
        }
        return String(chars)
    }

    private companion object {
        private const val BUFFER_SIZE = 8192
        private const val CACHE_PATH_SEGMENTS = 5
        private const val URL_TRAILING_SEGMENTS = 3
        private const val HEX_CHARS_PER_BYTE = 2
        private const val BYTE_MASK = 0xFF
        private const val NIBBLE_BITS = 4
        private const val NIBBLE_MASK = 0x0F
    }
}
