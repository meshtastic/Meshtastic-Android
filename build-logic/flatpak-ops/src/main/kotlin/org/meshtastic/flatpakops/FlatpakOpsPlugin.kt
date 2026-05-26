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
import groovy.json.JsonSlurper
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
 * URL is authoritative (taken straight from the build op); the on-disk file is found via Gradle's files-2.1 layout,
 * with a Module-Metadata-aware fallback for jars whose cache name differs from their URL name; SHA-256 is computed from
 * that exact file.
 *
 * Internal APIs touched (acceptable trade-off; same path flatpak-gradle-generator uses):
 * - org.gradle.internal.operations.BuildOperationListener / BuildOperationListenerManager
 * - org.gradle.internal.resource.ExternalResourceReadBuildOperationType
 * - org.gradle.api.internal.project.ProjectInternal (for .services)
 */
class FlatpakOpsPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        check(target == target.rootProject) { "meshtastic.flatpak-ops must be applied to the root project" }

        // Prefer the URL set populated by gradle/init-scripts/flatpak-ops.init.gradle.kts.
        // The init script attaches its listener BEFORE any plugin/project resolution, so it
        // captures bootstrap downloads (kotlin-dsl plugin marker, build-logic deps) that a
        // listener registered here would miss. If the init script wasn't passed via -I, we
        // fall back to a locally-attached listener — incomplete for build-logic deps but
        // useful for developer debugging. No buildFinished cleanup here: this plugin loads in
        // every normal build, and gradle.buildFinished is incompatible with the configuration
        // cache. The fallback is rarely used and the per-build leak is benign.
        @Suppress("UNCHECKED_CAST")
        val capturedUrls: MutableSet<String> =
            (target.gradle.extensions.findByName("flatpakOpsCapturedUrls") as? MutableSet<String>)
                ?: ConcurrentHashMap.newKeySet<String>().also { fallback ->
                    val manager = (target as ProjectInternal).services.get(BuildOperationListenerManager::class.java)
                    manager.addListener(OpListener(fallback))
                    target.logger.warn(
                        "flatpak-ops: init script not loaded; build-logic bootstrap URLs will be missing. " +
                            "Pass -I gradle/init-scripts/flatpak-ops.init.gradle.kts for a complete manifest.",
                    )
                }

        val outputProvider = target.layout.buildDirectory.file("flatpak-ops-sources.json")

        target.tasks.register("captureFlatpakSources") {
            group = "flatpak"
            description = "Emit flatpak-sources.json from URLs captured via BuildOperationListener."
            outputs.upToDateWhen { false }
            // Order after the resolution-emitting tasks so we don't snapshot capturedUrls before
            // their downloads happen. mustRunAfter is conditional — only the scheduled task enforces.
            mustRunAfter(":desktopApp:assemble", ":desktopApp:packageUberJarForCurrentOS")
            val proj = target
            val urlsRef = capturedUrls
            val outFile = outputProvider
            doLast { writeSources(proj, urlsRef.toList(), outFile.get().asFile) }
        }
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
                    val groupPath = group.replace('.', '/')
                    // dest-filename tracks the URL, not the cache: Gradle Module Metadata can declare
                    // files[].name ≠ files[].url, and the offline repo must serve at the URL path.
                    val urlFilename = URI(url).path.trimEnd('/').substringAfterLast('/')
                    val entry =
                        mutableMapOf<String, Any>(
                            "type" to "file",
                            "url" to url,
                            "sha256" to sha256(cacheFile),
                            "dest" to "offline-repository/$groupPath/$artifact/$version",
                            "dest-filename" to urlFilename,
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
     * Map a Maven URL to its file in Gradle's files-2.1 cache (layout:
     * `<group-with-dots>/<artifact>/<version>/<content-sha1>/<filename>`). Group is derived from the URL path —
     * `(artifact, version, filename)` is not unique across groups (e.g. androidx.annotation:annotation:1.10.0 collides
     * with org.jetbrains.compose.annotation-internal:annotation:1.10.0), so we probe every suffix of the leading path
     * segments and the longest match wins (this also strips arbitrary repo prefixes like `/maven2/`, `/m2/`).
     *
     * Two-tier lookup: the fast path assumes the cache filename matches the URL filename (true for pom/module always,
     * and most jars). For jars where Gradle Module Metadata renames the artifact locally (files[].name ≠ files[].url —
     * e.g. com.mikepenz:aboutlibraries-compose-core-jvm publishes aboutlibraries-compose-core-jvm-14.2.1.jar but caches
     * it as aboutlibraries-compose-jvm.jar) we parse the sibling .module file to resolve the real cache path.
     */
    private fun locateCacheFile(filesRoot: File, url: String): File? {
        val path = URI(url).path.trimEnd('/').split('/').filter { it.isNotEmpty() }
        val tail = path.takeLast(URL_TRAILING_SEGMENTS)
        if (!filesRoot.isDirectory || tail.size < URL_TRAILING_SEGMENTS) return null
        val (artifact, version, filename) = tail
        val groupCandidates = path.dropLast(URL_TRAILING_SEGMENTS)
        // files-2.1 uses dot-joined group as a SINGLE directory, e.g. `androidx.annotation/`, not
        // `androidx/annotation/` — so join with '.' here.
        return groupCandidates.indices.firstNotNullOfOrNull { start ->
            val groupDir = groupCandidates.drop(start).joinToString(".")
            val versionDir =
                File(filesRoot, "$groupDir/$artifact/$version").takeIf(File::isDirectory)
                    ?: return@firstNotNullOfOrNull null
            val shaDirs = versionDir.listFiles { f -> f.isDirectory } ?: return@firstNotNullOfOrNull null
            shaDirs.map { shaDir -> File(shaDir, filename) }.firstOrNull { it.isFile }
                ?: filename.takeIf { it.endsWith(".jar") }?.let { resolveJarViaModuleMetadata(versionDir, shaDirs, it) }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun resolveJarViaModuleMetadata(versionDir: File, shaDirs: Array<File>, urlFilename: String): File? {
        val moduleFile =
            shaDirs.firstNotNullOfOrNull { dir ->
                dir.listFiles()?.firstOrNull { it.isFile && it.name.endsWith(".module") }
            }
        val entry =
            moduleFile
                ?.let { runCatching { JsonSlurper().parse(it) as? Map<String, Any?> }.getOrNull() }
                ?.let { it["variants"] as? List<Map<String, Any?>> }
                ?.flatMap { (it["files"] as? List<Map<String, Any?>>).orEmpty() }
                ?.firstOrNull { it["url"] == urlFilename }
        val name = entry?.get("name") as? String
        val sha1 = entry?.get("sha1") as? String
        return if (name != null && sha1 != null) File(versionDir, "$sha1/$name").takeIf(File::isFile) else null
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
