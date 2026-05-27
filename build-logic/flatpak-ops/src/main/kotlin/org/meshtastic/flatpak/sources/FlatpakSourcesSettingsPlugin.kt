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
import org.gradle.internal.operations.notify.BuildOperationFinishedNotification
import org.gradle.internal.operations.notify.BuildOperationNotificationListener
import org.gradle.internal.operations.notify.BuildOperationNotificationListenerRegistrar
import org.gradle.internal.operations.notify.BuildOperationProgressNotification
import org.gradle.internal.operations.notify.BuildOperationStartedNotification
import org.gradle.internal.resource.ExternalResourceReadBuildOperationType
import java.util.concurrent.ConcurrentHashMap

/**
 * Settings plugin that captures external resource URLs from the very start of the build.
 *
 * **Strategy (two tiers):**
 *
 * 1. Attempts [BuildOperationNotificationListenerRegistrar] — a replay-buffered registrar
 *    that delivers ALL build operations from JVM start for 100% capture. Only one listener
 *    is allowed; if Develocity (or another tool) claims it first, we proceed to tier 2.
 *
 * 2. Falls back to [BuildEventListenerRegistryInternal.onOperationCompletion] — the same
 *    API used by GitHub's dependency-submission action. Supports multiple listeners (coexists
 *    with Develocity), proper BuildService lifecycle, but no replay. Paired with settings
 *    classpath introspection to reconstruct URLs for downloads that occurred before attach.
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

        val services = (settings.gradle as GradleInternal).services
        val replayRegistered = tryReplayRegistrar(services, capturedUrls)

        if (!replayRegistered) {
            // Tier 2: BuildEventListenerRegistryInternal (multi-listener, no replay).
            // Same pattern as gradle/github-dependency-graph-gradle-plugin.
            registerViaBuildEventRegistry(settings, capturedUrls)
            introspectSettingsClasspath(settings, capturedUrls)
        }

        // Auto-apply the project plugin to the root project.
        settings.gradle.rootProject {
            plugins.apply(FlatpakSourcesPlugin::class.java)
        }
    }

    /**
     * Tier 1: Replay-buffered [BuildOperationNotificationListenerRegistrar].
     * Returns true if successful, false if the slot is already occupied.
     */
    private fun tryReplayRegistrar(
        services: org.gradle.internal.service.ServiceRegistry,
        urls: MutableSet<String>,
    ): Boolean {
        return try {
            val registrar = services.get(BuildOperationNotificationListenerRegistrar::class.java)
            registrar.register(
                object : BuildOperationNotificationListener {
                    override fun started(notification: BuildOperationStartedNotification) = Unit
                    override fun progress(notification: BuildOperationProgressNotification) = Unit
                    override fun finished(notification: BuildOperationFinishedNotification) {
                        val details = notification.notificationOperationDetails
                            as? ExternalResourceReadBuildOperationType.Details ?: return
                        if (notification.notificationOperationFailure != null) return
                        urls.add(details.location)
                    }
                },
            )
            true
        } catch (_: IllegalStateException) {
            false
        }
    }

    /**
     * Tier 2: Register via [BuildEventListenerRegistryInternal.onOperationCompletion].
     * Uses a [BuildService] for proper lifecycle and supports multiple concurrent listeners.
     */
    private fun registerViaBuildEventRegistry(settings: Settings, urls: MutableSet<String>) {
        val serviceProvider: Provider<UrlCaptureBuildService> =
            settings.gradle.sharedServices.registerIfAbsent(
                "flatpakSourcesUrlCapture",
                UrlCaptureBuildService::class.java,
            ) {}

        // Inject the shared URL set into the service instance.
        serviceProvider.get().capturedUrls = urls

        val registry = (settings.gradle as GradleInternal)
            .services
            .get(BuildEventListenerRegistryInternal::class.java)

        registry.onOperationCompletion(serviceProvider)
    }

    /**
     * Enumerates artifacts already resolved in the settings buildscript classpath.
     * Reconstructs download URLs for events that occurred before our listener attached.
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
                urls.add("$basePath/$filename")

                val pomFile = "$name-$version.pom"
                val moduleFile = "$name-$version.module"
                if (filename != pomFile) urls.add("$basePath/$pomFile")
                if (filename != moduleFile) urls.add("$basePath/$moduleFile")

                val markerPath = "$repoUrl/$markerGroupPath/$markerGroup.gradle.plugin/$version"
                urls.add("$markerPath/$markerPom")
            }
        }
    }
}

/**
 * BuildService that implements [BuildOperationListener] for use with
 * [BuildEventListenerRegistryInternal.onOperationCompletion].
 *
 * This is the same pattern used by `gradle/github-dependency-graph-gradle-plugin`.
 * Multiple services can be registered simultaneously — coexists with Develocity.
 */
abstract class UrlCaptureBuildService :
    BuildService<BuildServiceParameters.None>,
    BuildOperationListener {

    /** Injected by the settings plugin after registration. */
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

