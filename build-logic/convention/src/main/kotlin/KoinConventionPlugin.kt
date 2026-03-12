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
import org.gradle.kotlin.dsl.dependencies
import org.meshtastic.buildlogic.libs
import org.meshtastic.buildlogic.plugin

class KoinConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = libs.plugin("koin-compiler").get().pluginId)

            val koinAnnotations = libs.findLibrary("koin-annotations").get()
            val koinCore = libs.findLibrary("koin-core").get()

            pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
                dependencies {
                    add("commonMainApi", koinCore)
                    add("commonMainApi", koinAnnotations)
                }
            }

            pluginManager.withPlugin("org.jetbrains.kotlin.android") {
                // If this is *only* an Android module (no KMP plugin)
                if (!pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
                    dependencies {
                        add("implementation", koinCore)
                        add("implementation", koinAnnotations)
                    }
                }
            }

            pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
                // If this is *only* a JVM module (no KMP plugin)
                if (!pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
                    dependencies {
                        add("implementation", koinCore)
                        add("implementation", koinAnnotations)
                    }
                }
            }
        }
    }
}
