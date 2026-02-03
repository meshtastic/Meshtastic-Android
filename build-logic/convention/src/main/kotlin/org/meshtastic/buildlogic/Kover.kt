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

import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

fun Project.configureKover() {
    extensions.configure<KoverProjectExtension> {
        reports {
            total {
                xml {
                    onCheck.set(true)
                }
                html {
                    onCheck.set(true)
                }
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

                    // Exclude UI components
                    annotatedBy("*Preview")

                    // Exclude declarations
                    annotatedBy(
                        "*.HiltAndroidApp",
                        "*.AndroidEntryPoint",
                        "*.Module",
                        "*.Provides",
                        "*.Binds",
                        "*.Composable",
                    )

                    // Suppress generated code
                    packages("hilt_aggregated_deps")
                    packages("org.meshtastic.core.strings")
                }
            }
        }
    }
}

/**
 * Configure Kover aggregation in a way that is compatible with Gradle Isolated Projects.
 * Instead of iterating through subprojects and checking plugins, we add all subprojects
 * as dependencies and let Kover resolve them.
 */
fun Project.configureKoverAggregation() {
    val subprojectPaths = subprojects.map { it.path }
    dependencies.apply {
        subprojectPaths.forEach { path ->
            add("kover", project(path))
        }
    }
}
