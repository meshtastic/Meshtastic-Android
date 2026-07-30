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
import org.koin.compiler.plugin.KoinGradleExtension
import org.meshtastic.buildlogic.libs
import org.meshtastic.buildlogic.plugin

class KoinConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            apply(plugin = libs.plugin("koin-compiler").get().pluginId)

            // Configure Koin K2 Compiler Plugin (1.1.0+)
            extensions.configure(KoinGradleExtension::class.java) {
                // 1.1.0 moved validation to the entry points, which suits this graph's shape, but
                // its definition index still can't resolve two structural patterns here: modules
                // reached through FlavorModule's nested `includes` are invisible to it, and DSL
                // declarations (desktopApp's whole root, workManagerFactory()) are never indexed at
                // all. Every entry point therefore fails on definitions that exist. Runtime graph
                // verification is handled by KoinVerificationTest instead.
                compileSafety.set(false)
            }

            val koinAnnotations = libs.findLibrary("koin-annotations").get()
            val koinCore = libs.findLibrary("koin-core").get()

            pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
                dependencies {
                    add("commonMainApi", koinCore)
                    add("commonMainApi", koinAnnotations)
                }
            }

            pluginManager.withPlugin("com.android.application") {
                // If this is *only* an Android module (no KMP plugin)
                if (!pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform")) {
                    dependencies {
                        add("implementation", koinCore)
                        add("implementation", koinAnnotations)
                    }
                }
            }

            pluginManager.withPlugin("com.android.library") {
                // If this is *only* an Android library module (no KMP plugin)
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
