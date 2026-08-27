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
import com.android.build.api.dsl.LibraryExtension
import com.android.compose.screenshot.tasks.PreviewScreenshotValidationTask
import com.gradle.develocity.agent.gradle.test.DevelocityTestConfiguration
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.meshtastic.buildlogic.library
import org.meshtastic.buildlogic.libs

/**
 * Convention for Compose Screenshot Testing modules (`:screenshot-tests`, `:docs-screenshots`).
 *
 * Owns the shared CST configuration: the `enableScreenshotTest` experimental flag, the test-retry opt-out (CST's custom
 * runner is incompatible with test retry), and the Compose Multiplatform + screenshot-validation dependencies.
 * Configuration-only: apply it ALONGSIDE `meshtastic.android.library[.compose]` and `com.android.compose.screenshot`,
 * which the module declares itself. It also owns the image-difference tolerance, which used to live in the module
 * scripts as `testOptions { screenshotTests { imageDifferenceThreshold } }` — alpha16 deleted that DSL and
 * conventions the engine threshold to 0f (pixel-exact), which fails 33 of our references. The task property the
 * DSL used to feed is still public, so the same tolerance is set directly on it here.
 */
class AndroidScreenshotConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.withPlugin("com.android.compose.screenshot") {
                extensions.configure<LibraryExtension> {
                    experimentalProperties["android.experimental.enableScreenshotTest"] = true
                }

                tasks.withType<PreviewScreenshotValidationTask>().configureEach {
                    testEngineInput.threshold.set(IMAGE_DIFFERENCE_THRESHOLD)
                }

                // CST screenshot tests use a custom runner incompatible with test retry
                tasks.withType<Test>().configureEach {
                    if (name.contains("ScreenshotTest", ignoreCase = true)) {
                        extensions.findByType(DevelocityTestConfiguration::class.java)?.testRetry {
                            maxRetries.set(0)
                        }
                    }
                }

                dependencies.add("implementation", libs.library("compose-multiplatform-foundation"))
                dependencies.add("implementation", libs.library("compose-multiplatform-material3"))
                dependencies.add("implementation", libs.library("compose-multiplatform-runtime"))
                dependencies.add("implementation", libs.library("compose-multiplatform-ui"))

                // Added lazily: AGP creates the screenshotTest configurations after plugin apply.
                configurations
                    .matching { it.name == "screenshotTestImplementation" }
                    .configureEach { project.dependencies.add(name, libs.library("screenshot-validation-api")) }
            }
        }
    }

    private companion object {
        const val IMAGE_DIFFERENCE_THRESHOLD = 0.0005f
    }
}
