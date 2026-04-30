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
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.meshtastic.buildlogic.library
import org.meshtastic.buildlogic.libs

/**
 * Convention plugin for KMP feature modules.
 *
 * Composes [KmpLibraryConventionPlugin], [KmpLibraryComposeConventionPlugin], and [KoinConventionPlugin] and wires the
 * common Compose / Lifecycle / Koin dependencies that every feature module needs. Feature `build.gradle.kts` files only
 * declare their module-specific deps.
 *
 * Modelled after the `AndroidFeatureImplConventionPlugin` pattern from
 * [Now in Android](https://github.com/android/nowinandroid).
 */
class KmpFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "meshtastic.kmp.library")
            apply(plugin = "meshtastic.kmp.library.compose")
            apply(plugin = "meshtastic.koin")

            extensions.configure<KotlinMultiplatformExtension> {
                sourceSets.getByName("commonMain").dependencies {
                    // Compose Multiplatform UI
                    implementation(libs.library("compose-multiplatform-animation"))
                    implementation(libs.library("compose-multiplatform-material3"))

                    // Lifecycle & ViewModel (JetBrains KMP forks — safe in commonMain)
                    implementation(libs.library("jetbrains-lifecycle-viewmodel-compose"))
                    implementation(libs.library("jetbrains-lifecycle-runtime-compose"))

                    // Koin ViewModel wiring
                    implementation(libs.library("koin-compose-viewmodel"))

                    // Logging
                    implementation(libs.library("kermit"))

                    // @Preview available in commonMain since CMP 1.11 (androidx.compose.ui.tooling.preview.Preview)
                    // org.jetbrains.compose.ui.tooling.preview.Preview is deprecated in 1.11
                    implementation(libs.library("compose-multiplatform-ui-tooling-preview"))
                }

                sourceSets.getByName("androidMain").dependencies {
                    // Common Android Compose dependencies
                    implementation(libs.library("accompanist-permissions"))
                    implementation(libs.library("androidx-activity-compose"))

                    implementation(libs.library("compose-multiplatform-ui"))
                }

                sourceSets.getByName("commonTest").dependencies { implementation(project(":core:testing")) }
            }
        }
    }
}
