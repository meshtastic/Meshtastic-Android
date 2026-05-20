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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.register

class FlatpakPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            tasks.register<GenerateFlatpakSourcesTask>("generateFlatpakSourcesFromCache") {
                val customCachePath = providers.gradleProperty("flatpak.cache.dir").orNull
                val defaultCachePath = java.io.File(gradle.gradleUserHomeDir, "caches/modules-2/files-2.1")

                if (customCachePath != null) {
                    cacheDir.set(layout.projectDirectory.dir(customCachePath))
                } else {
                    cacheDir.set(defaultCachePath)
                }

                outputFile.set(layout.projectDirectory.file("flatpak-sources.json"))
            }
        }
    }
}
