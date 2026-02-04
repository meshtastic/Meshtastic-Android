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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.dependencies
import org.meshtastic.buildlogic.library
import org.meshtastic.buildlogic.libs

class HiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = "com.google.devtools.ksp")

            dependencies {
                "ksp"(libs.library("hilt-compiler"))
                "implementation"(libs.library("hilt-android"))
            }

            // Add support for Jvm Module, base on org.jetbrains.kotlin.jvm
            pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
                dependencies {
                    "implementation"(libs.library("hilt-core"))
                }
            }

            pluginManager.withPlugin("com.android.base") {
                apply(plugin = "dagger.hilt.android.plugin")
            }

            pluginManager.withPlugin("org.jetbrains.kotlin.plugin.compose") {
                dependencies {
                    "implementation"(libs.library("androidx-hilt-lifecycle-viewmodel-compose"))
                }
            }
        }
    }
}
