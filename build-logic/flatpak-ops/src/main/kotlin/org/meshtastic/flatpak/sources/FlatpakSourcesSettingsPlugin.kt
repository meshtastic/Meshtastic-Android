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
 * Uses [BuildEventListenerRegistryInternal.onOperationCompletion] with a [BuildService] —
 * the same pattern as `gradle/github-dependency-graph-gradle-plugin`. Supports unlimited
 * concurrent listeners and coexists with Develocity or any other build tooling.
 *
 * A post-build cache scan (executed during the `captureFlatpakSources` task) fills gaps
 * for downloads that occurred before the listener attached — namely included build
 * plugin resolution (e.g. build-logic's kotlin-dsl) and settings plugin bootstrap.
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

        // Collect repo URLs from root settings (available now) for the cache scan at task time.
        val repoUrls = collectRepoUrls(settings)
        settings.gradle.extensions.add("flatpakSourcesRepoUrls", repoUrls)

        // Auto-apply the project plugin to the root project.
        settings.gradle.rootProject {
            plugins.apply(FlatpakSourcesPlugin::class.java)
        }
    }

    /**
     * Collects all Maven repository URLs from root settings and well-known repos.
     * No network calls — just reads configuration. Used at task time for cache scan.
     */
    private fun collectRepoUrls(settings: Settings): List<String> {
        val repos = settings.pluginManagement.repositories
            .filterIsInstance<org.gradle.api.artifacts.repositories.MavenArtifactRepository>()
            .map { it.url.toString().trimEnd('/') }
            .filter { !it.startsWith("file:") }
            .toMutableList()

        // Ensure well-known repos are included (build-logic typically uses these)
        val wellKnown = listOf(
            "https://repo1.maven.org/maven2",
            "https://plugins.gradle.org/m2",
            "https://dl.google.com/dl/android/maven2",
        )
        for (repo in wellKnown) {
            if (repo !in repos) repos.add(repo)
        }

        return repos.distinct()
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

