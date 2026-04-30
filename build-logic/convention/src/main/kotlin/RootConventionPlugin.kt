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
import org.meshtastic.buildlogic.configureDokkaAggregation
import org.meshtastic.buildlogic.configureGraphTasks
import org.meshtastic.buildlogic.configureKover
import org.meshtastic.buildlogic.configureKoverAggregation

/**
 * Root convention plugin applied to the top-level project.
 *
 * Configures Dokka aggregation, Kover aggregation, graph tasks, and the `kmpSmokeCompile` lifecycle task. All
 * subproject references use explicit path strings derived from `settings.gradle.kts` includes to avoid `subprojects {}`
 * / `allprojects {}` iteration, which performs cross-project configuration access incompatible with Gradle Isolated
 * Projects.
 */
class RootConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        require(target.path == ":")
        with(target) {
            apply(plugin = "org.jetbrains.dokka")
            configureDokkaAggregation(ALL_MODULES)

            apply(plugin = "org.jetbrains.kotlinx.kover")
            configureKover()
            configureKoverAggregation(ALL_MODULES)

            // Register graph tasks on the root project itself
            configureGraphTasks()

            registerKmpSmokeCompileTask()
        }
    }
}

/**
 * Registers a `kmpSmokeCompile` lifecycle task that depends on `compileKotlinJvm` and `compileKotlinIosSimulatorArm64`
 * tasks from all KMP modules using task path strings.
 *
 * Non-KMP modules simply won't have these tasks, so the path-based dependencies will be silently ignored.
 */
private fun Project.registerKmpSmokeCompileTask() {
    tasks.register("kmpSmokeCompile") {
        group = "verification"
        description = "Compile all KMP modules for JVM and iOS Simulator ARM64 targets."

        KMP_MODULES.forEach { path ->
            dependsOn("$path:compileKotlinJvm")
            dependsOn("$path:compileKotlinIosSimulatorArm64")
        }
    }
}

/** All modules included in `settings.gradle.kts`. Update this list when adding or removing modules. */
private val ALL_MODULES =
    listOf(
        ":app",
        ":core:api",
        ":core:barcode",
        ":core:ble",
        ":core:common",
        ":core:data",
        ":core:database",
        ":core:datastore",
        ":core:di",
        ":core:domain",
        ":core:model",
        ":core:navigation",
        ":core:network",
        ":core:nfc",
        ":core:prefs",
        ":core:proto",
        ":core:repository",
        ":core:service",
        ":core:resources",
        ":core:takserver",
        ":core:testing",
        ":core:ui",
        ":feature:intro",
        ":feature:messaging",
        ":feature:connections",
        ":feature:map",
        ":feature:node",
        ":feature:settings",
        ":feature:firmware",
        ":feature:wifi-provision",
        ":feature:widget",
        ":desktop",
    )

/**
 * Modules that apply the KMP plugin and should be compiled for JVM + iOS targets. Excludes pure-Android modules (:app,
 * :core:api, :core:barcode, :feature:widget) and the desktop JVM-only module.
 */
private val KMP_MODULES =
    ALL_MODULES.filter { path -> path !in setOf(":app", ":core:api", ":core:barcode", ":feature:widget", ":desktop") }
