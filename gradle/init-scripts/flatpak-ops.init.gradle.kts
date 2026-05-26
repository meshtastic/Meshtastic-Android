/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * Init script for meshtastic.flatpak-ops. Attaches a BuildOperationListener
 * BEFORE any project or plugin resolution happens — which is necessary because
 * the flatpak-ops plugin itself lives in build-logic, and any artifacts pulled
 * to bootstrap build-logic (kotlin-dsl plugin marker, detekt, etc.) would be
 * invisible to a listener registered later from a root-project plugin.
 *
 * Captured URLs are stored on `gradle.extensions` under the key below; the
 * captureFlatpakSources task (registered by FlatpakOpsPlugin) reads them.
 *
 * Pass to Gradle via:
 *   ./gradlew -I gradle/init-scripts/flatpak-ops.init.gradle.kts ...
 */

import org.gradle.api.internal.GradleInternal
import org.gradle.internal.operations.BuildOperationDescriptor
import org.gradle.internal.operations.BuildOperationListener
import org.gradle.internal.operations.BuildOperationListenerManager
import org.gradle.internal.operations.OperationFinishEvent
import org.gradle.internal.operations.OperationIdentifier
import org.gradle.internal.operations.OperationProgressEvent
import org.gradle.internal.operations.OperationStartEvent
import org.gradle.internal.resource.ExternalResourceReadBuildOperationType
import java.util.concurrent.ConcurrentHashMap

val capturedUrls: MutableSet<String> = ConcurrentHashMap.newKeySet()
gradle.extensions.add("flatpakOpsCapturedUrls", capturedUrls)

val manager =
    (gradle as GradleInternal).services.get(BuildOperationListenerManager::class.java)

val listener =
    object : BuildOperationListener {
        override fun started(op: BuildOperationDescriptor, e: OperationStartEvent) = Unit
        override fun progress(id: OperationIdentifier, e: OperationProgressEvent) = Unit
        override fun finished(op: BuildOperationDescriptor, e: OperationFinishEvent) {
            val details = op.details as? ExternalResourceReadBuildOperationType.Details ?: return
            if (e.failure != null) return
            capturedUrls.add(details.location)
        }
    }
manager.addListener(listener)

// Detach at build finish so a long-lived daemon doesn't accumulate one listener per build.
// buildFinished is deprecated and breaks the configuration cache — fine here because this
// script is only loaded via `-I` in the verify flow, which uses --no-configuration-cache.
// Revisit (Flow API or AutoCloseable BuildService) when we drop Gradle 9 support.
@Suppress("DEPRECATION")
gradle.buildFinished { manager.removeListener(listener) }
