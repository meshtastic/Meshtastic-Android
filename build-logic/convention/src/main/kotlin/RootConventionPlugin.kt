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
import org.meshtastic.buildlogic.configureDokkaAggregation
import org.meshtastic.buildlogic.configureGraphTasks
import org.meshtastic.buildlogic.configureKover
import org.meshtastic.buildlogic.configureKoverAggregation

class RootConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        require(target.path == ":")
        with(target) {
            apply(plugin = "org.jetbrains.dokka")
            configureDokkaAggregation()

            apply(plugin = "org.jetbrains.kotlinx.kover")
            configureKover()
            configureKoverAggregation()

            subprojects { configureGraphTasks() }

            registerKmpSmokeCompileTask()
        }
    }
}

/**
 * Registers a `kmpSmokeCompile` lifecycle task that auto-discovers all KMP modules
 * and depends on their `compileKotlinJvm` and `compileKotlinIosSimulatorArm64` tasks.
 *
 * This replaces the long explicit task list in CI, auto-maintaining as modules are added.
 */
private fun Project.registerKmpSmokeCompileTask() {
    tasks.register("kmpSmokeCompile") {
        group = "verification"
        description = "Compile all KMP modules for JVM and iOS Simulator ARM64 targets."

        subprojects.forEach { sub ->
            sub.pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
                dependsOn(sub.tasks.matching { it.name == "compileKotlinJvm" })
                dependsOn(sub.tasks.matching { it.name == "compileKotlinIosSimulatorArm64" })
            }
        }
    }
}
