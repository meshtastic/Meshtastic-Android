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
import org.gradle.api.initialization.Settings
import org.gradle.api.internal.GradleInternal
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.internal.build.event.BuildEventListenerRegistryInternal
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.internal.resource.ExternalResourceReadBuildOperationType
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * Settings plugin that captures external resource URLs throughout the entire build.
 *
 * Uses [BuildEventListenerRegistryInternal.onOperationCompletion] with a [BuildService] —
 * the same pattern as `gradle/github-dependency-graph-gradle-plugin`. Supports unlimited
 * concurrent listeners and coexists with Develocity or any other build tooling.
 *
 * Settings classpath introspection fills the gap for downloads that occurred before the
 * listener attached (pluginManagement resolution, settings plugin bootstrap).
 *
 * ```kotlin
 * // settings.gradle.kts
 * plugins {
 *     id("org.meshtastic.flatpak.sources.settings") version "0.1.0"
 * }
 * ```
 */
class FlatpakSourcesSettingsPlugin : Plugin<Settings> {

    override fun apply(settings: Settings) {
        val capturedUrls: MutableSet<String> = ConcurrentHashMap.newKeySet()
        settings.gradle.extensions.add("flatpakSourcesCapturedUrls", capturedUrls)

        // Register BuildService listener via BuildEventListenerRegistryInternal.
        val serviceProvider: Provider<UrlCaptureBuildService> =
            settings.gradle.sharedServices.registerIfAbsent(
                "flatpakSourcesUrlCapture",
                UrlCaptureBuildService::class.java,
            ) {}

        serviceProvider.get().capturedUrls = capturedUrls

        val registry = (settings.gradle as GradleInternal)
            .services
            .get(BuildEventListenerRegistryInternal::class.java)

        registry.onOperationCompletion(serviceProvider)

        // Reconstruct URLs for downloads that occurred before our listener attached.
        introspectSettingsClasspath(settings, capturedUrls)

        // Auto-apply the project plugin to the root project.
        settings.gradle.rootProject {
            plugins.apply(FlatpakSourcesPlugin::class.java)
        }
    }

    /**
     * Enumerates artifacts already resolved in the settings buildscript classpath.
     * Reconstructs download URLs for pluginManagement resolution and settings plugins
     * that downloaded before our BuildService listener was active.
     *
     * For each artifact, we probe candidate URLs with a HEAD request to avoid emitting
     * invalid URLs (e.g. Develocity JAR against Google Maven) that would cause
     * flatpak-builder to fail with a 404.
     */
    private fun introspectSettingsClasspath(settings: Settings, urls: MutableSet<String>) {
        val repos = settings.pluginManagement.repositories
            .filterIsInstance<org.gradle.api.artifacts.repositories.MavenArtifactRepository>()
            .map { it.url.toString().trimEnd('/') }
            .filter { !it.startsWith("file:") }

        val classpath = settings.buildscript.configurations.findByName("classpath") ?: return
        if (!classpath.isCanBeResolved) return

        classpath.resolvedConfiguration.resolvedArtifacts.forEach { artifact ->
            val mid = artifact.moduleVersion.id
            val group = mid.group
            val name = mid.name
            val version = mid.version
            val filename = artifact.file.name

            if (version == "unspecified") return@forEach

            val groupPath = group.replace('.', '/')
            val markerGroup = "$group.$name"
            val markerGroupPath = markerGroup.replace('.', '/')
            val markerPom = "$markerGroup.gradle.plugin-$version.pom"

            for (repoUrl in repos) {
                val basePath = "$repoUrl/$groupPath/$name/$version"
                val jarUrl = "$basePath/$filename"

                // Validate the primary artifact URL exists before emitting any URLs for this repo
                if (!headCheck(jarUrl)) continue

                urls.add(jarUrl)
                val pomFile = "$name-$version.pom"
                val moduleFile = "$name-$version.module"
                if (filename != pomFile) urls.add("$basePath/$pomFile")
                if (filename != moduleFile) urls.add("$basePath/$moduleFile")

                val markerPath = "$repoUrl/$markerGroupPath/$markerGroup.gradle.plugin/$version"
                urls.add("$markerPath/$markerPom")
            }
        }
    }

    /** Quick HEAD check to verify a URL is reachable (2xx/3xx). */
    private fun headCheck(url: String): Boolean =
        try {
            val conn = URI(url).toURL().openConnection() as HttpURLConnection
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

    private companion object {
        private const val HEAD_TIMEOUT_MS = 5_000
        private val HTTP_OK_RANGE = 200..399
    }
}

/**
 * BuildService implementing [BuildOperationListener] for use with
 * [BuildEventListenerRegistryInternal.onOperationCompletion].
 *
 * Same pattern as `gradle/github-dependency-graph-gradle-plugin`.
 * Supports multiple concurrent listeners — coexists with Develocity.
 */
abstract class UrlCaptureBuildService :
    BuildService<BuildServiceParameters.None>,
    BuildOperationListener {

    internal lateinit var capturedUrls: MutableSet<String>

    override fun started(op: BuildOperationDescriptor, e: OperationStartEvent) = Unit
    override fun progress(id: OperationIdentifier, e: OperationProgressEvent) = Unit
    override fun finished(op: BuildOperationDescriptor, e: OperationFinishEvent) {
        val details = op.details
            as? ExternalResourceReadBuildOperationType.Details ?: return
        if (e.failure != null) return
        capturedUrls.add(details.location)
    }
}

