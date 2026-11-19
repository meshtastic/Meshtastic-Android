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
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.meshtastic.buildlogic.libs
import org.meshtastic.buildlogic.plugin

class AboutLibrariesConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.plugin("aboutlibraries").get().pluginId)

            extensions.configure<AboutLibrariesExtension> {
                // release.yml passes aboutLibraries.release=true to the Google and desktop release builds only.
                // For fdroid/reproducible builds, offlineMode=true ensures no network calls
                // and deterministic output. See: https://github.com/meshtastic/Meshtastic-Android/issues/3231
                val isReleaseBuild =
                    providers.gradleProperty("aboutLibraries.release").map { it.toBoolean() }.getOrElse(false)
                val ghToken = providers.environmentVariable("GITHUB_TOKEN")

                // Shared directory of manually supplied license texts (config/aboutlibraries/licenses/*.json),
                // matched by `hash`. Offline mode never fetches SPDX/remote texts, so without these the
                // open-source-licenses screen would be missing license bodies in fdroid/reproducible builds.
                // Supplying them also makes release builds deterministic (no network fetch when content exists).
                val licenseConfigDir = isolated.rootProject.projectDirectory.dir("config/aboutlibraries")

                offlineMode.set(!isReleaseBuild)

                collect {
                    configPath.set(licenseConfigDir)
                    // Wired as providers, never read here: CI issues a new token per step, and a token read at
                    // configuration time becomes a configuration cache input that no later step can reuse.
                    val fetchRemote = ghToken.map { isReleaseBuild }.orElse(false)
                    fetchRemoteLicense.set(fetchRemote)
                    fetchRemoteFunding.set(fetchRemote)
                    gitHubApiToken.set(ghToken)
                }
                export { excludeFields.set(listOf("generated")) }
                library {
                    duplicationMode.set(DuplicateMode.MERGE)
                    duplicationRule.set(DuplicateRule.SIMPLE)
                }
            }

            pluginManager.withPlugin("com.android.application") { exportToJvmResources() }
            pluginManager.withPlugin("org.jetbrains.kotlin.jvm") { exportToJvmResources() }
            pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") { exportToWasmJsResources() }
        }
    }

    // androidApp and desktopApp read the JSON from their classpath.
    private fun Project.exportToJvmResources() {
        extensions.configure<AboutLibrariesExtension> {
            export { outputFile.set(file("src/main/resources/aboutlibraries.json")) }
        }

        // Ensure aboutlibraries.json is generated before resource processing.
        // The file is gitignored and must be generated fresh each build.
        // offlineMode is already true when aboutLibraries.release is not passed,
        // ensuring deterministic output for fdroid/reproducible builds.
        // See: https://github.com/meshtastic/Meshtastic-Android/issues/3231
        tasks
            .matching {
                it.name.startsWith("process") && (it.name.endsWith("Resources") || it.name.endsWith("JavaRes"))
            }
            .configureEach { dependsOn("exportLibraryDefinitions") }
    }

    // webApp fetches the JSON from its distribution root. Only the wasmJs graph is exported: npm packages, such as
    // core:database's sqlite-wasm, are not Gradle modules and stay out of the list.
    private fun Project.exportToWasmJsResources() {
        val outputDir = layout.buildDirectory.dir("generated/aboutLibraries/wasmJs")
        extensions.configure<AboutLibrariesExtension> {
            exports { register("wasmJs") { outputFile.set(outputDir.map { it.file("aboutlibraries.json") }) } }
        }
        extensions.configure<KotlinMultiplatformExtension> {
            sourceSets
                .matching { it.name == "wasmJsMain" }
                .configureEach {
                    // The plugin registers this task when the wasmJs target appears, so it is named rather than looked
                    // up.
                    resources.srcDir(files(outputDir).builtBy("exportLibraryDefinitionsWasmJs"))
                }
        }
    }
}
