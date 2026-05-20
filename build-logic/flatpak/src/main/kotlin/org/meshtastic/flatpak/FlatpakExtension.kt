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
package org.meshtastic.flatpak

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

/**
 * DSL extension for configuring the Flatpak source manifest generator.
 *
 * ```kotlin
 * flatpak {
 *     snapshotRepoUrl.set("https://central.sonatype.com/repository/maven-snapshots")
 *     assembleTask.set(":desktopApp:assemble")
 * }
 * ```
 */
abstract class FlatpakExtension {

    /** Gradle cache directory to scan for dependency artifacts. */
    abstract val cacheDir: DirectoryProperty

    /** Output path for the generated flatpak-sources.json manifest. */
    abstract val outputFile: RegularFileProperty

    /** Base URL of the Maven snapshot repository (no trailing slash). */
    abstract val snapshotRepoUrl: Property<String>

    /** Task path to depend on, ensuring the cache is fully populated before scanning. */
    abstract val assembleTask: Property<String>
}
