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
import java.util.concurrent.ConcurrentHashMap

/**
 * Settings plugin that captures external resource URLs throughout the entire build.
 *
 * Uses [BuildEventListenerRegistryInternal.onOperationCompletion] with a [BuildService] — the same pattern as
 * `gradle/github-dependency-graph-gradle-plugin`. Supports unlimited concurrent listeners and coexists with Develocity
 * or any other build tooling.
 *
 * A post-build cache scan (executed during the `captureFlatpakSources` task) fills gaps for downloads that occurred
 * before the listener attached — namely included build plugin resolution (e.g. build-logic's kotlin-dsl) and settings
 * plugin bootstrap.
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

        val registry = (settings.gradle as GradleInternal).services.get(BuildEventListenerRegistryInternal::class.java)

        registry.onOperationCompletion(serviceProvider)

        // Defer repo URL collection to rootProject{} so dependencyResolutionManagement.repositories
        // is fully populated (it's configured after plugins{} in settings.gradle.kts).
        settings.gradle.rootProject {
            val repoUrls = collectRepoUrls(settings)
            settings.gradle.extensions.add("flatpakSourcesRepoUrls", repoUrls)
            plugins.apply(FlatpakSourcesPlugin::class.java)
        }
    }

    /**
     * Collects Maven repository URLs from pluginManagement and dependencyResolutionManagement, plus well-known repos.
     * No network calls. Must be called after settings evaluation so that dependencyResolutionManagement.repositories is
     * populated.
     */
    @Suppress("UnstableApiUsage")
    private fun collectRepoUrls(settings: Settings): List<String> {
        val repos = mutableListOf<String>()

        fun addIfAbsent(url: String) {
            if (url !in repos) repos.add(url)
        }

        settings.pluginManagement.repositories
            .filterIsInstance<org.gradle.api.artifacts.repositories.MavenArtifactRepository>()
            .map { it.url.toString().trimEnd('/') }
            .filter { !it.startsWith("file:") }
            .forEach(::addIfAbsent)

        // Also include project-dependency repos (jitpack, snapshots, etc.) so the cache scan
        // can find artifacts resolved from dependencyResolutionManagement.repositories.
        settings.dependencyResolutionManagement.repositories
            .filterIsInstance<org.gradle.api.artifacts.repositories.MavenArtifactRepository>()
            .map { it.url.toString().trimEnd('/') }
            .filter { !it.startsWith("file:") }
            .forEach(::addIfAbsent)

        // Ensure well-known repos are included (build-logic typically uses these)
        listOf(
            "https://repo1.maven.org/maven2",
            "https://plugins.gradle.org/m2",
            "https://dl.google.com/dl/android/maven2",
        )
            .forEach(::addIfAbsent)

        return repos
    }
}

/**
 * BuildService implementing [BuildOperationListener] for use with
 * [BuildEventListenerRegistryInternal.onOperationCompletion].
 *
 * Same pattern as `gradle/github-dependency-graph-gradle-plugin`. Supports multiple concurrent listeners — coexists
 * with Develocity.
 */
abstract class UrlCaptureBuildService :
    BuildService<BuildServiceParameters.None>,
    BuildOperationListener {

    internal lateinit var capturedUrls: MutableSet<String>

    override fun started(op: BuildOperationDescriptor, e: OperationStartEvent) = Unit

    override fun progress(id: OperationIdentifier, e: OperationProgressEvent) = Unit

    override fun finished(op: BuildOperationDescriptor, e: OperationFinishEvent) {
        val details = op.details as? ExternalResourceReadBuildOperationType.Details ?: return
        if (e.failure != null) return
        capturedUrls.add(details.location)
    }
}
