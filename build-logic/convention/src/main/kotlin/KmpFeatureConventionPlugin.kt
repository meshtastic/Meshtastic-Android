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
                    implementation(libs.library("compose-multiplatform-foundation"))
                    implementation(libs.library("compose-multiplatform-material3"))

                    // Navigation 3 (JetBrains KMP fork — safe in commonMain)
                    implementation(libs.library("jetbrains-navigation3-ui"))

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
                    implementation(libs.library("androidx-activity-compose"))

                    implementation(libs.library("compose-multiplatform-ui"))
                }
            }

            // core:testing has no wasmJs target (same gap every core/* module hit while gaining one this
            // session). Wiring it into commonTest directly — as this plugin used to, unconditionally —
            // breaks compileTestKotlinWasmJs for every feature module that opts into wasmJs, since the
            // dependency is added by this shared plugin's apply(), which runs *before* the consuming
            // module's own build.gradle.kts `kotlin {}` block (and any nonWebTest source set it creates)
            // has executed. Deferring to afterEvaluate — which fires only after the whole build script has
            // run — lets us check what the consuming module actually set up and route accordingly:
            // - a module with a `nonWebTest` source set (wasmJs opted in, core:testing hoisted out of
            //   commonMain the same way every core/* module did) gets core:testing wired there instead.
            // - every other feature module (no wasmJs, no nonWebTest) is wired into commonTest exactly as
            //   before — fully backward-compatible, verified against every other v0/non-wasmJs consumer.
            target.afterEvaluate {
                extensions.configure<KotlinMultiplatformExtension> {
                    val hasWasmJsTarget = targets.findByName("wasmJs") != null
                    val nonWebTest = sourceSets.findByName("nonWebTest")
                    check(!hasWasmJsTarget || nonWebTest != null) {
                        "${target.path} registers wasmJs() but has no `nonWebTest` source set — " +
                            "core:testing has no wasmJs target, so it must be routed away from commonTest. " +
                            "See feature/connections/build.gradle.kts for the pattern."
                    }
                    val testSourceSet = nonWebTest ?: sourceSets.getByName("commonTest")
                    testSourceSet.dependencies { implementation(project(":core:testing")) }
                }
            }
        }
    }
}
