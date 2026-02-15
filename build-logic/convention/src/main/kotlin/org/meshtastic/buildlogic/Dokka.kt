/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.buildlogic

import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.dokka.gradle.DokkaExtension
import java.net.URI

fun Project.configureDokka() {
    extensions.configure<DokkaExtension> {
        // Use the full project path as the module name to ensure uniqueness
        moduleName.set(project.path.removePrefix(":").replace(":", "-").ifEmpty { project.name })

        dokkaSourceSets.configureEach {
            perPackageOption {
                matchingRegex.set("hilt_aggregated_deps")
                suppress.set(true)
            }
            perPackageOption {
                matchingRegex.set("org.meshtastic.core.strings.*")
                suppress.set(true)
            }

            // Dokka 2.x requires each source file to belong to exactly one source set.
            val baseSourceSets = listOf(
                "main",
                "commonMain",
                "androidMain",
                "fdroid",
                "google",
                "release"
            )

            val isCoreSourceSet = name in baseSourceSets
            suppress.set(!isCoreSourceSet)

            sourceLink {
                enableJdkDocumentationLink.set(true)
                enableKotlinStdLibDocumentationLink.set(true)
                reportUndocumented.set(true)

                // Standardized repo-root based source links
                localDirectory.set(project.projectDir)
                val relativePath =
                    project.projectDir.relativeTo(rootProject.projectDir).path.replace("\\", "/")
                remoteUrl.set(URI("https://github.com/meshtastic/Meshtastic-Android/blob/main/$relativePath"))
                remoteLineSuffix.set("#L")
            }
        }
    }
}

/**
 * Configure Dokka aggregation in a way that is compatible with Gradle Isolated Projects.
 */
fun Project.configureDokkaAggregation() {
    extensions.configure<DokkaExtension> {
        moduleName.set("Meshtastic App")
        dokkaPublications.configureEach {
            suppressInheritedMembers.set(true)
        }
    }

    subprojects.forEach { subproject ->
        subproject.pluginManager.withPlugin("org.jetbrains.dokka") {
            dependencies.add("dokka", subproject)
        }
    }
}
