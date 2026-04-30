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

import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.extensions.FailOnSeverity
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

internal fun Project.configureDetekt(extension: DetektExtension) = extension.apply {
    toolVersion.set(libs.version("detekt"))
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    buildUponDefaultConfig.set(true)
    allRules.set(false)
    parallel.set(true)
    basePath.set(rootDir)
    failOnSeverity.set(FailOnSeverity.Error)

    // Use per-module baseline files to suppress pre-existing violations introduced by the
    // detekt 2.0 upgrade. New violations will still be caught. Remove baselines incrementally
    // as modules are cleaned up.
    val baselineFile = project.file("detekt-baseline.xml")
    if (baselineFile.exists()) {
        baseline.set(baselineFile)
    }

    // Default sources
    source.setFrom(
        files(
            "src/main/java",
            "src/main/kotlin",
            "src/commonMain/kotlin",
            "src/androidMain/kotlin",
            "src/jvmMain/kotlin",
        ),
    )

    tasks.withType<Detekt>().configureEach {
        val isCi = project.findProperty("ci") == "true"
        reports {
            checkstyle {
                required.set(true)
                outputLocation.set(layout.buildDirectory.file("reports/detekt/detekt.xml"))
            }
            sarif {
                required.set(true)
                outputLocation.set(layout.buildDirectory.file("reports/detekt/detekt.sarif"))
            }
            // In CI, only generate checkstyle and sarif (needed for GitHub reporting).
            // Skip html and markdown to save processing time.
            html {
                required.set(!isCi)
                outputLocation.set(layout.buildDirectory.file("reports/detekt/detekt.html"))
            }
            markdown {
                required.set(!isCi)
                outputLocation.set(layout.buildDirectory.file("reports/detekt/detekt.md"))
            }
        }
    }
    dependencies {
        "detektPlugins"(libs.library("detekt-formatting"))
        "detektPlugins"(libs.library("detekt-compose"))
    }
}
