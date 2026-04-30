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

import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

fun Project.configureKover() {
    val isCi = providers.gradleProperty("ci").map { it.toBoolean() }.getOrElse(false)
    extensions.configure<KoverProjectExtension> {
        reports {
            total {
                // In CI, reports are generated explicitly per-shard; skip automatic generation on check.
                xml { onCheck.set(!isCi) }
                html { onCheck.set(!isCi) }
            }
            filters {
                excludes {
                    // Exclude generated classes
                    classes("*_Impl")
                    classes("*Binding")
                    classes("*Factory")
                    classes("*.BuildConfig")
                    classes("*.R")
                    classes("*.R$*")

                    // Exclude iOS compile-only stubs (no test execution on these targets)
                    classes("*NoopStubs*")

                    // Exclude UI components
                    annotatedBy("*Preview")

                    // Exclude declarations
                    annotatedBy("*.Module", "*.Provides", "*.Binds", "*.Composable")

                    // Suppress generated code
                    packages("koin_aggregated_deps")
                    packages("org.meshtastic.core.resources")
                }
            }
        }
    }
}

/**
 * Configure Kover aggregation for the root project.
 *
 * Accepts an explicit list of subproject paths to avoid `subprojects {}` iteration, which is incompatible with Gradle
 * Isolated Projects. The list should match the modules declared in `settings.gradle.kts`.
 */
fun Project.configureKoverAggregation(subprojectPaths: List<String>) {
    subprojectPaths.forEach { path -> dependencies.add("kover", project(path)) }
}
