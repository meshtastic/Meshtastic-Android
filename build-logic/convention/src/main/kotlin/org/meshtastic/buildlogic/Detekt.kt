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

import io.gitlab.arturbosch.detekt.Detekt
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.named

internal fun Project.configureDetekt(extension: DetektExtension) = extension.apply {
    toolVersion = libs.version("detekt")
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    buildUponDefaultConfig = true
    allRules = false
    
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

    tasks.named<Detekt>("detekt") {
        reports {
            xml.required.set(true)
            html.required.set(true)
            txt.required.set(true)
            sarif.required.set(true)
            md.required.set(true)
        }
        // Use project-specific build directory for reports to avoid conflicts
        reports.xml.outputLocation.set(layout.buildDirectory.file("reports/detekt/detekt.xml"))
        reports.html.outputLocation.set(layout.buildDirectory.file("reports/detekt/detekt.html"))
        reports.txt.outputLocation.set(layout.buildDirectory.file("reports/detekt/detekt.txt"))
        reports.sarif.outputLocation.set(layout.buildDirectory.file("reports/detekt/detekt.sarif"))
        reports.md.outputLocation.set(layout.buildDirectory.file("reports/detekt/detekt.md"))
    }
    dependencies {
        "detektPlugins"(libs.library("detekt-formatting"))
        "detektPlugins"(libs.library("detekt-compose"))
    }
}
