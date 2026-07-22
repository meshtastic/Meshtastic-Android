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
package org.meshtastic.buildlogic

import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.project
import org.jetbrains.dokka.gradle.DokkaExtension
import java.net.URI

/**
 * Patched jackson-core forced onto Dokka's classpaths.
 *
 * Dokka 2.x pulls jackson-core transitively (dokka-core -> jackson-dataformat-xml / -module-kotlin),
 * and the 2.15.x line it selects is vulnerable to GHSA-r7wm-3cxj-wff9 (async-parser `maxNumberLength`
 * bypass, CWE-770). Dokka is a build-time-only documentation tool, so this never ships in the app, but
 * pinning the patched line keeps the resolved dependency graph clean and closes the Dependabot alert.
 * 2.18.8 is the first patched release and the closest maintenance line to Dokka's transitive 2.15.3.
 */
private const val PATCHED_JACKSON_CORE = "com.fasterxml.jackson.core:jackson-core:2.18.8"

/** Force [PATCHED_JACKSON_CORE] on every Dokka configuration of this project (no-op elsewhere). */
private fun Project.pinPatchedJacksonOnDokkaClasspaths() {
    configurations.configureEach {
        if (name.contains("dokka", ignoreCase = true)) {
            resolutionStrategy.force(PATCHED_JACKSON_CORE)
        }
    }
}

fun Project.configureDokka() {
    pinPatchedJacksonOnDokkaClasspaths()
    extensions.configure<DokkaExtension> {
        // Use the full project path as the module name to ensure uniqueness
        moduleName.set(project.path.removePrefix(":").replace(":", "-").ifEmpty { project.name })

        dokkaSourceSets.configureEach {
            perPackageOption {
                matchingRegex.set("koin_aggregated_deps")
                suppress.set(true)
            }
            perPackageOption {
                matchingRegex.set("org.meshtastic.core.resources.*")
                suppress.set(true)
            }

            // Dokka 2.x requires each source file to belong to exactly one source set.
            val baseSourceSets =
                listOf("main", "commonMain", "androidMain", "jvmMain", "jvmAndroidMain", "fdroid", "google", "release")

            val isCoreSourceSet = name in baseSourceSets
            suppress.set(!isCoreSourceSet)

            sourceLink {
                enableJdkDocumentationLink.set(true)
                enableKotlinStdLibDocumentationLink.set(true)
                reportUndocumented.set(true)

                // Standardized repo-root based source links
                localDirectory.set(project.projectDir)
                val rootDir = project.isolated.rootProject.projectDirectory.asFile
                val relativePath = project.projectDir.relativeTo(rootDir).path.replace("\\", "/")
                remoteUrl.set(URI("https://github.com/meshtastic/Meshtastic-Android/blob/main/$relativePath"))
                remoteLineSuffix.set("#L")
            }
        }
    }
}

/**
 * Configure Dokka aggregation for the root project.
 *
 * Accepts an explicit list of subproject paths to avoid `subprojects {}` iteration, which is incompatible with Gradle
 * Isolated Projects. The list should match the modules declared in `settings.gradle.kts`.
 */
fun Project.configureDokkaAggregation(subprojectPaths: List<String>) {
    pinPatchedJacksonOnDokkaClasspaths()
    extensions.configure<DokkaExtension> {
        moduleName.set("Meshtastic App")
        dokkaPublications.configureEach { suppressInheritedMembers.set(true) }
    }

    // Add each subproject as a Dokka dependency using declared paths rather than
    // iterating live subproject objects. This avoids cross-project configuration
    // access and is compatible with Gradle Isolated Projects.
    subprojectPaths.forEach { path -> dependencies.add("dokka", dependencies.project(path)) }
}
