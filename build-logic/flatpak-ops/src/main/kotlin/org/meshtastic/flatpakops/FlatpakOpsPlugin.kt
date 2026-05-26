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
        check(target == target.rootProject) { "meshtastic.flatpak-ops must be applied to the root project" }

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
            // Must run AFTER the task that triggers resolution. Without this, Gradle's scheduler
            // may interleave/parallelize this task with :desktopApp:assemble, causing us to write
            // the file before the downloads we want to capture have happened.
            mustRunAfter(":desktopApp:assemble")
            val proj = target
            val urlsRef = capturedUrls
            val outFile = outputProvider
            doLast { writeSources(proj, urlsRef.toList(), outFile.get().asFile) }
        }
        // Listener is intentionally NOT removed on task completion; it stays attached until JVM
        // exit. Removal is unsafe when our task races against other resolution-emitting tasks.
        // Known limitation: in a long-lived Gradle daemon, capturedUrls accumulates across builds.
        // We can't clear it at task start (that would erase what was captured during assemble).
        // The CI workflow uses a fresh isolated GRADLE_USER_HOME, so this only affects local
        // developer use — and re-emitting a superset of URLs is harmless.
    }

    private class OpListener(private val urls: MutableSet<String>) : BuildOperationListener {
        override fun started(op: BuildOperationDescriptor, e: OperationStartEvent) = Unit

        override fun progress(id: OperationIdentifier, e: OperationProgressEvent) = Unit

        override fun finished(op: BuildOperationDescriptor, e: OperationFinishEvent) {
            val details = op.details as? ExternalResourceReadBuildOperationType.Details ?: return
            if (e.failure != null) return
            // No host/scheme filtering here: non-Maven URLs (distribution zips, repo listings, etc.)
            // naturally drop out in writeSources() when locateCacheFile() can't find them under
            // files-2.1. Keeping this listener permissive avoids hardcoding repo allowlists.
            urls.add(details.location)
        }
    }

    private fun writeSources(project: Project, urls: List<String>, output: File) {
        val filesRoot = File(project.gradle.gradleUserHomeDir, "caches/modules-2/files-2.1")
        val entries: List<Map<String, Any>> =
            urls
                .distinct()
                .filterNot { url ->
                    // Exclude sources/javadoc jars: they're not needed for an offline build and
                    // inflate the manifest. Match on URL filename, not cache-relative path.
                    val tail = url.substringAfterLast('/')
                    tail.endsWith("-sources.jar") || tail.endsWith("-javadoc.jar")
                }
                .sorted()
                .mapNotNull { url ->
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
                    val entry =
                        mutableMapOf<String, Any>(
                            "type" to "file",
                            "url" to url,
                            "sha256" to sha256(cacheFile),
                            "dest" to "offline-repository/$groupPath/$artifact/$version",
                            "dest-filename" to onDiskFilename,
                        )
                    mirrorsFor(url).takeIf { it.isNotEmpty() }?.let { entry["mirror-urls"] = it }
                    entry
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
     * files-2.1 layout is `<group-with-dots>/<artifact>/<version>/<content-sha1>/<filename>`. The (artifact, version,
     * filename) triple is NOT unique across groups (e.g. androidx.annotation:annotation:1.10.0.module collides with
     * org.jetbrains.compose.annotation-internal:annotation:1.10.0.module), so the group MUST be derived from the URL
     * path. We probe every suffix of the path's leading segments against the cache layout — the longest match wins
     * (strips arbitrary repo prefixes like `/maven2/`, `/dl/android/maven2/`, `/m2/` without hardcoding them).
     */
    private fun locateCacheFile(filesRoot: File, url: String): File? {
        val path = URI(url).path.trimEnd('/').split('/').filter { it.isNotEmpty() }
        val tail = path.takeLast(URL_TRAILING_SEGMENTS)
        if (!filesRoot.isDirectory || tail.size < URL_TRAILING_SEGMENTS) return null
        val (artifact, version, filename) = tail
        val groupCandidates = path.dropLast(URL_TRAILING_SEGMENTS)
        // files-2.1 uses dot-joined group as a SINGLE directory, e.g. `androidx.annotation/`,
        // not `androidx/annotation/`. So join URL segments with '.' here.
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
     * Derive fallback mirror URLs from the primary URL's host. Only Maven Central has well-known public mirrors; for
     * everything else (Google, JitPack, Gradle plugin portal, snapshot repos), we trust the primary URL since these
     * hosts don't have stable mirrors anyway. The URL itself is authoritative — we just rewrite the host while
     * preserving the path.
     */
    private fun mirrorsFor(url: String): List<String> {
        val uri = runCatching { URI(url) }.getOrNull()
        val host = uri?.host
        val path = uri?.rawPath
        return if (host != null && path != null && host in MAVEN_CENTRAL_HOSTS) {
            MAVEN_CENTRAL_HOSTS.filter { it != host }.map { "https://$it$path" } +
                "https://maven-central.storage-download.googleapis.com$path"
        } else {
            emptyList()
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
        private val MAVEN_CENTRAL_HOSTS = listOf("repo.maven.apache.org", "repo1.maven.org")
        private const val BUFFER_SIZE = 8192
        private const val CACHE_PATH_SEGMENTS = 5
        private const val URL_TRAILING_SEGMENTS = 3
        private const val HEX_CHARS_PER_BYTE = 2
        private const val BYTE_MASK = 0xFF
        private const val NIBBLE_BITS = 4
        private const val NIBBLE_MASK = 0x0F
    }
}
