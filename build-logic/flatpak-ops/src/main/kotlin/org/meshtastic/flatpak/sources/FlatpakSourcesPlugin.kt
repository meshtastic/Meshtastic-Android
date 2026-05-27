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
import org.gradle.api.artifacts.ModuleVersionIdentifier
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
 * Apply the companion settings plugin in `settings.gradle.kts` for complete capture from build start:
 * ```kotlin
 * plugins { id("org.meshtastic.flatpak.sources.settings") version "..." }
 * ```
 * The settings plugin auto-applies this plugin to the root project; there is no need to apply both.
 */
class FlatpakSourcesPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        check(target == target.rootProject) { "org.meshtastic.flatpak.sources must be applied to the root project" }

        val extension = target.extensions.create("flatpakSources", FlatpakSourcesExtension::class.java)
        extension.outputFile.convention(target.layout.buildDirectory.file("flatpak-sources.json"))

        // The settings plugin registers this URL set before any resolution happens. If it isn't present
        // (plugin applied directly without the settings plugin), register a fallback listener.
        // It won't capture bootstrap downloads but will get everything from this point forward.
        @Suppress("UNCHECKED_CAST")
        val capturedUrls: MutableSet<String> =
            (target.gradle.extensions.findByName(CAPTURED_URLS_KEY) as? MutableSet<String>)
                ?: ConcurrentHashMap.newKeySet<String>().also { fallback ->
                    val manager = (target as ProjectInternal).services.get(BuildOperationListenerManager::class.java)
                    manager.addListener(OpListener(fallback))
                    target.logger.warn(
                        "flatpak-sources: settings plugin not applied — bootstrap downloads will be missing. " +
                            "Add id(\"org.meshtastic.flatpak.sources.settings\") to settings.gradle.kts.",
                    )
                }

        target.tasks.register("captureFlatpakSources") {
            group = "flatpak"
            description = "Emit flatpak-sources.json from URLs captured via BuildOperationListener."
            outputs.upToDateWhen { false }
            outputs.file(extension.outputFile)

            val afterTasks = extension.mustRunAfterTasks.get()
            if (afterTasks.isNotEmpty()) mustRunAfter(afterTasks)

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
                val platformUrls =
                    resolvePlatformArtifacts(proj, platforms.getOrElse(emptySet()), platformDeps.getOrElse(emptySet()))

                @Suppress("UNCHECKED_CAST")
                val repoUrls =
                    proj.gradle.extensions.findByName(REPO_URLS_KEY) as? List<String> ?: emptyList()
                val cacheUrls =
                    if (repoUrls.isNotEmpty()) {
                        scanCacheForMissingArtifacts(filesRoot, urlsRef, repoUrls, proj.logger)
                    } else {
                        emptyList()
                    }

                val allUrls = urlsRef.toList() + platformUrls + cacheUrls
                val writer =
                    SourcesWriter(
                        filesRoot = filesRoot,
                        destPrefix = destPrefix.get(),
                        excludeSuffixes = excludeSuffixes.get(),
                        generateMirrors = generateMirrors.get(),
                        repoUrls = repoUrls,
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
        internal const val CAPTURED_URLS_KEY = "flatpakSourcesCapturedUrls"
        internal const val REPO_URLS_KEY = "flatpakSourcesRepoUrls"

        /**
         * Force-resolves platform-specific dependencies that wouldn't normally resolve on the current host OS.
         * Downloads them into the standard Gradle cache so [SourcesWriter] can find them via the cache locator.
         *
         * @return URLs reconstructed from the resolved artifact coordinates and repositories.
         */
        fun resolvePlatformArtifacts(
            project: Project,
            platforms: Set<String>,
            dependencyTemplates: Set<String>,
        ): List<String> {
            if (platforms.isEmpty() || dependencyTemplates.isEmpty()) return emptyList()

            val deps =
                platforms.flatMap { platform ->
                    dependencyTemplates.map { template ->
                        project.dependencies.create(template.replace("{platform}", platform))
                    }
                }

            val config = project.configurations.detachedConfiguration()
            deps.forEach { config.dependencies.add(it) }
            config.isTransitive = false

            val repoUrls =
                project.repositories
                    .filterIsInstance<org.gradle.api.artifacts.repositories.MavenArtifactRepository>()
                    .map { it.url.toString().trimEnd('/') }
                    .filter { !it.startsWith("file:") }

            return try {
                config.resolvedConfiguration.resolvedArtifacts.flatMap { artifact ->
                    val mid = artifact.moduleVersion.id
                    project.logger.lifecycle(
                        "flatpak-sources: force-resolved {} (platform artifact)",
                        "${mid.group}:${mid.name}:${mid.version}",
                    )
                    artifactToUrls(mid, artifact.file.name, repoUrls)
                }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                project.logger.warn("flatpak-sources: platform resolution failed — {}", e.message)
                emptyList()
            }
        }

        private fun artifactToUrls(
            mid: ModuleVersionIdentifier,
            filename: String,
            repoUrls: List<String>,
        ): List<String> {
            val groupPath = mid.group.replace('.', '/')
            val pomFile = "${mid.name}-${mid.version}.pom"
            return repoUrls.flatMap { repoUrl ->
                val base = "$repoUrl/$groupPath/${mid.name}/${mid.version}"
                buildList {
                    add("$base/$filename")
                    if (filename != pomFile) add("$base/$pomFile")
                }
            }
        }

        /**
         * Scans the Gradle file cache for artifacts not already in [capturedUrls]. Picks up artifacts resolved
         * during included build configuration (e.g. build-logic's kotlin-dsl plugin) that the listener missed.
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

            filesRoot.listFiles { f -> f.isDirectory }?.forEach { groupDir ->
                val groupPath = groupDir.name.replace('.', '/')
                groupDir.listFiles { f -> f.isDirectory }?.forEach { artifactDir ->
                    artifactDir.listFiles { f -> f.isDirectory }?.forEach { versionDir ->
                        urls.addAll(scanVersionDir(versionDir, groupPath, artifactDir.name, capturedUrls, repoUrls))
                    }
                }
            }

            if (urls.isNotEmpty()) {
                logger.lifecycle("flatpak-sources: cache scan found {} additional URLs", urls.size)
            }
            return urls
        }

        private fun scanVersionDir(
            versionDir: File,
            groupPath: String,
            artifact: String,
            capturedUrls: Set<String>,
            repoUrls: List<String>,
        ): List<String> {
            val version = versionDir.name
            val files =
                versionDir.listFiles { f -> f.isDirectory }
                    ?.flatMap { hashDir -> hashDir.listFiles()?.filter { it.isFile }?.toList() ?: emptyList() }
                    ?: emptyList()

            val result = mutableListOf<String>()
            for (file in files) {
                val filename = file.name
                val alreadyCaptured =
                    repoUrls.any { repo -> capturedUrls.contains("$repo/$groupPath/$artifact/$version/$filename") }
                if (alreadyCaptured) continue
                for (repoUrl in repoUrls) {
                    val fileUrl = "$repoUrl/$groupPath/$artifact/$version/$filename"
                    if (headCheck(fileUrl)) {
                        result.add(fileUrl)
                        break
                    }
                }
            }
            return result
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
        private val HTTP_OK_RANGE = 200..299
    }
}
