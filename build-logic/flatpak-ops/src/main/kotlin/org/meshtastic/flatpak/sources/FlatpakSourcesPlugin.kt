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
package org.meshtastic.flatpak.sources

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
import org.meshtastic.flatpak.sources.internal.SourcesWriter
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Captures every external resource URL Gradle reads via the internal BuildOperationListener API and emits a
 * Flathub-compliant flatpak-sources.json at build finish.
 *
 * No heuristics: URL is authoritative (taken straight from the build op), the on-disk file is found via Gradle's
 * documented files-2.1 layout, and SHA-256 is computed from that exact file.
 *
 * For complete captures (including build-logic bootstrap downloads), pair with the init script:
 * ```
 * ./gradlew -I gradle/init-scripts/flatpak-sources.init.gradle.kts ...
 * ```
 *
 * Internal APIs used (acceptable trade-off — same approach as flatpak-gradle-generator):
 * - org.gradle.internal.operations.BuildOperationListener / BuildOperationListenerManager
 * - org.gradle.internal.resource.ExternalResourceReadBuildOperationType
 * - org.gradle.api.internal.project.ProjectInternal (for .services)
 */
class FlatpakSourcesPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        check(target == target.rootProject) { "org.meshtastic.flatpak.sources must be applied to the root project" }

        val extension = target.extensions.create("flatpakSources", FlatpakSourcesExtension::class.java)
        extension.outputFile.convention(target.layout.buildDirectory.file("flatpak-sources.json"))

        // Prefer the URL set populated by the companion init script.
        // The init script attaches its listener BEFORE any plugin/project resolution, so it
        // captures bootstrap downloads (kotlin-dsl plugin marker, build-logic deps) that a
        // listener registered here would miss.
        @Suppress("UNCHECKED_CAST")
        val capturedUrls: MutableSet<String> =
            (target.gradle.extensions.findByName(extension.capturedUrlsExtensionName.get()) as? MutableSet<String>)
                ?: ConcurrentHashMap.newKeySet<String>().also { fallback ->
                    val manager = (target as ProjectInternal).services.get(BuildOperationListenerManager::class.java)
                    manager.addListener(OpListener(fallback))
                    target.logger.warn(
                        "flatpak-sources: init script not loaded; build-logic bootstrap URLs will be missing. " +
                            "Pass -I <path-to-init-script> for a complete manifest.",
                    )
                }

        target.tasks.register("captureFlatpakSources") {
            group = "flatpak"
            description = "Emit flatpak-sources.json from URLs captured via BuildOperationListener."
            outputs.upToDateWhen { false }

            val afterTasks = extension.mustRunAfterTasks.get()
            if (afterTasks.isNotEmpty()) {
                mustRunAfter(afterTasks)
            }

            val proj = target
            val urlsRef = capturedUrls
            val outFile = extension.outputFile
            val destPrefix = extension.destPrefix
            val excludeSuffixes = extension.excludeSuffixes
            val generateMirrors = extension.generateMirrors
            val platforms = extension.targetPlatforms
            val platformDeps = extension.platformDependencies
            val filesRoot = File(target.gradle.gradleUserHomeDir, "caches/modules-2/files-2.1")

            doLast {
                // Force-resolve platform-specific native dependencies not resolved on this host
                val platformUrls = resolvePlatformArtifacts(proj, platforms.getOrElse(emptySet()), platformDeps.getOrElse(emptySet()))

                // Scan the Gradle cache for artifacts missed by the listener
                // (e.g. build-logic plugin resolution that happened before listener attached)
                @Suppress("UNCHECKED_CAST")
                val repoUrls = proj.gradle.extensions.findByName("flatpakSourcesRepoUrls") as? List<String>
                    ?: emptyList()
                val cacheUrls = if (repoUrls.isNotEmpty()) {
                    scanCacheForMissingArtifacts(filesRoot, urlsRef, repoUrls, proj.logger)
                } else emptyList()

                val allUrls = urlsRef.toList() + platformUrls + cacheUrls
                val writer =
                    SourcesWriter(
                        filesRoot = filesRoot,
                        destPrefix = destPrefix.get(),
                        excludeSuffixes = excludeSuffixes.get(),
                        generateMirrors = generateMirrors.get(),
                        logger = proj.logger,
                    )
                writer.write(allUrls, outFile.get().asFile)
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

    companion object {
        /**
         * Force-resolves platform-specific dependencies that wouldn't normally resolve on the
         * current host OS. Downloads them into the standard Gradle cache so [SourcesWriter] can
         * find them via [CacheFileLocator].
         *
         * @return URLs reconstructed from the resolved artifact coordinates and repositories.
         */
        fun resolvePlatformArtifacts(
            project: Project,
            platforms: Set<String>,
            dependencyTemplates: Set<String>,
        ): List<String> {
            if (platforms.isEmpty() || dependencyTemplates.isEmpty()) return emptyList()

            val deps = platforms.flatMap { platform ->
                dependencyTemplates.map { template ->
                    project.dependencies.create(template.replace("{platform}", platform))
                }
            }

            val config = project.configurations.detachedConfiguration(*deps.toTypedArray())
            config.isTransitive = false

            val urls = mutableListOf<String>()
            val repoUrls = project.repositories
                .filterIsInstance<org.gradle.api.artifacts.repositories.MavenArtifactRepository>()
                .map { it.url.toString().trimEnd('/') }
                .filter { !it.startsWith("file:") }

            try {
                config.resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
                    val mid = artifact.moduleVersion.id
                    val groupPath = mid.group.replace('.', '/')
                    val filename = artifact.file.name
                    for (repoUrl in repoUrls) {
                        val base = "$repoUrl/$groupPath/${mid.name}/${mid.version}"
                        urls.add("$base/$filename")
                        // Include POM for offline resolution
                        val pomFile = "${mid.name}-${mid.version}.pom"
                        if (filename != pomFile) urls.add("$base/$pomFile")
                    }
                    project.logger.lifecycle(
                        "flatpak-sources: force-resolved {} (platform artifact)",
                        "${mid.group}:${mid.name}:${mid.version}",
                    )
                }
            } catch (e: Exception) {
                project.logger.warn("flatpak-sources: platform resolution failed — {}", e.message)
            }
            return urls
        }

        /**
         * Scans the Gradle file cache for artifacts not already in [capturedUrls].
         * This picks up artifacts resolved during included build configuration
         * (e.g. build-logic's kotlin-dsl plugin) that our listener missed.
         *
         * Uses HEAD checks to validate URLs before emitting.
         */
        fun scanCacheForMissingArtifacts(
            filesRoot: File,
            capturedUrls: Set<String>,
            repoUrls: List<String>,
            logger: org.gradle.api.logging.Logger,
        ): List<String> {
            if (!filesRoot.isDirectory) return emptyList()
            val urls = mutableListOf<String>()
            var scanned = 0

            filesRoot.listFiles { f -> f.isDirectory }?.forEach { groupDir ->
                val group = groupDir.name
                val groupPath = group.replace('.', '/')
                groupDir.listFiles { f -> f.isDirectory }?.forEach { artifactDir ->
                    val artifact = artifactDir.name
                    artifactDir.listFiles { f -> f.isDirectory }?.forEach { versionDir ->
                        val version = versionDir.name
                        val files = versionDir.listFiles { f -> f.isDirectory }
                            ?.flatMap { hashDir ->
                                hashDir.listFiles()?.filter { it.isFile }?.toList() ?: emptyList()
                            } ?: emptyList()

                        for (file in files) {
                            val filename = file.name
                            // Skip if already captured by the listener from any repo
                            val alreadyCaptured = repoUrls.any { repo ->
                                capturedUrls.contains("$repo/$groupPath/$artifact/$version/$filename")
                            }
                            if (alreadyCaptured) continue

                            // HEAD-check to find the correct repo
                            for (repoUrl in repoUrls) {
                                val fileUrl = "$repoUrl/$groupPath/$artifact/$version/$filename"
                                if (headCheck(fileUrl)) {
                                    urls.add(fileUrl)
                                    scanned++
                                    break
                                }
                            }
                        }
                    }
                }
            }
            if (scanned > 0) {
                logger.lifecycle("flatpak-sources: cache scan found {} additional URLs", scanned)
            }
            return urls
        }

        private fun headCheck(url: String): Boolean =
            try {
                val conn = java.net.URI(url).toURL().openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "HEAD"
                conn.connectTimeout = HEAD_TIMEOUT_MS
                conn.readTimeout = HEAD_TIMEOUT_MS
                conn.instanceFollowRedirects = true
                val code = conn.responseCode
                conn.disconnect()
                code in HTTP_OK_RANGE
            } catch (_: Exception) {
                false
            }

        private const val HEAD_TIMEOUT_MS = 5_000
        private val HTTP_OK_RANGE = 200..399
    }
}
