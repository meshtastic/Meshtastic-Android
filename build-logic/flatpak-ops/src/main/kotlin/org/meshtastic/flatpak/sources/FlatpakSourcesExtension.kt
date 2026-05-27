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

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import javax.inject.Inject

/**
 * Configuration extension for the `org.meshtastic.flatpak.sources` plugin.
 *
 * ```kotlin
 * flatpakSources {
 *     outputFile.set(layout.buildDirectory.file("flatpak-sources.json"))
 *     destPrefix.set("offline-repository")
 *     mustRunAfterTasks.set(listOf(":app:assemble"))
 *     generateMirrors.set(true)
 *     excludeSuffixes.set(setOf("-sources.jar", "-javadoc.jar"))
 * }
 * ```
 */
abstract class FlatpakSourcesExtension @Inject constructor(objects: ObjectFactory) {

    /** Output file for the generated Flatpak sources JSON manifest. */
    val outputFile: RegularFileProperty = objects.fileProperty()

    /** Destination path prefix inside the Flatpak sandbox (e.g., "offline-repository"). */
    val destPrefix: Property<String> = objects.property(String::class.java).convention("offline-repository")

    /** Task paths that the capture task must run after (ensuring dependencies are resolved first). */
    val mustRunAfterTasks: ListProperty<String> = objects.listProperty(String::class.java)

    /** Whether to generate Maven Central mirror URLs in the output. */
    val generateMirrors: Property<Boolean> = objects.property(Boolean::class.java).convention(true)

    /** URL filename suffixes to exclude from the manifest (e.g., sources/javadoc jars). */
    val excludeSuffixes: SetProperty<String> =
        objects.setProperty(String::class.java).convention(setOf("-sources.jar", "-javadoc.jar"))

    /** Name of the Gradle extension used to share captured URLs from the init script. */
    val capturedUrlsExtensionName: Property<String> =
        objects.property(String::class.java).convention("flatpakSourcesCapturedUrls")

    /** Where the init script should be installed (relative to project root). */
    val initScriptFile: RegularFileProperty = objects.fileProperty()

    /**
     * Platform targets whose native artifacts should be force-resolved during capture.
     * Use for platform-specific dependencies not resolved on the generation host OS
     * (e.g., building manifest on macOS but targeting a Linux Flatpak).
     *
     * Values are Compose/Skiko platform identifiers: `linux-x64`, `linux-arm64`, etc.
     */
    val targetPlatforms: SetProperty<String> = objects.setProperty(String::class.java)

    /**
     * Platform-specific dependencies to force-resolve for the target platforms.
     * Each entry is a Maven coordinate template with `{platform}` placeholder:
     * e.g., `org.jetbrains.skiko:skiko-awt-runtime-{platform}:0.144.6`
     *
     * The `{platform}` token is replaced with each value in [targetPlatforms].
     */
    val platformDependencies: SetProperty<String> = objects.setProperty(String::class.java)
}
