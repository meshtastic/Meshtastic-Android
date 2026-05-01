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
import com.mikepenz.aboutlibraries.plugin.AboutLibrariesExtension
import com.mikepenz.aboutlibraries.plugin.DuplicateMode
import com.mikepenz.aboutlibraries.plugin.DuplicateRule
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.meshtastic.buildlogic.libs
import org.meshtastic.buildlogic.plugin

class AboutLibrariesConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.plugin("aboutlibraries").get().pluginId)

            extensions.configure<AboutLibrariesExtension> {
                // aboutLibraries.release=true is only passed for google builds (see Fastfile).
                // For fdroid/reproducible builds, offlineMode=true ensures no network calls
                // and deterministic output. See: https://github.com/meshtastic/Meshtastic-Android/issues/3231
                val isReleaseBuild =
                    providers.gradleProperty("aboutLibraries.release").map { it.toBoolean() }.getOrElse(false)
                val ghToken = providers.environmentVariable("GITHUB_TOKEN")

                offlineMode.set(!isReleaseBuild)

                collect {
                    fetchRemoteLicense.set(isReleaseBuild && ghToken.isPresent)
                    fetchRemoteFunding.set(isReleaseBuild && ghToken.isPresent)
                    if (ghToken.isPresent) {
                        gitHubApiToken.set(ghToken.get())
                    }
                }
                export {
                    excludeFields.set(listOf("generated"))
                    outputFile.set(file("src/main/resources/aboutlibraries.json"))
                }
                library {
                    duplicationMode.set(DuplicateMode.MERGE)
                    duplicationRule.set(DuplicateRule.SIMPLE)
                }
            }

            // Ensure aboutlibraries.json is always up-to-date during the build.
            // This is required since AboutLibraries v11+ no longer auto-exports.
            // For fdroid builds, skip re-export to preserve reproducible builds (RB) —
            // the committed aboutlibraries.json is used as-is.
            // See: https://github.com/meshtastic/Meshtastic-Android/issues/3231
            tasks
                .matching {
                    it.name.startsWith("process") &&
                        (it.name.endsWith("Resources") || it.name.endsWith("JavaRes")) &&
                        !it.name.contains("Fdroid", ignoreCase = true)
                }
                .configureEach { dependsOn("exportLibraryDefinitions") }

            // Gradle 9.5 strict task-dependency validation: fdroid variants read from
            // src/main/resources/ (where exportLibraryDefinitions writes). Even though
            // fdroid uses the committed file as-is, we must declare ordering to satisfy
            // the implicit dependency checker.
            tasks
                .matching {
                    it.name.startsWith("process") &&
                        it.name.endsWith("JavaRes") &&
                        it.name.contains("Fdroid", ignoreCase = true)
                }
                .configureEach { mustRunAfter("exportLibraryDefinitions") }
        }
    }
}
