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

import com.skydoves.compose.stability.gradle.StabilityAnalyzerExtension
import com.skydoves.compose.stability.gradle.StabilityCheckTask
import com.skydoves.compose.stability.gradle.StabilityDumpTask
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

internal fun Project.configureComposeCompiler() {
    val stabilityConfig = isolated.rootProject.projectDirectory.file("compose_compiler_config.conf")
    apply(plugin = libs.plugin("compose-stability-analyzer").get().pluginId)
    extensions.configure<StabilityAnalyzerExtension> { stabilityConfigurationFiles.add(stabilityConfig) }
    // The stability tasks read compileAndroidMain's output, but only wire compile tasks whose name contains "Kotlin".
    pluginManager.withPlugin("com.android.kotlin.multiplatform.library") {
        tasks.withType<StabilityDumpTask>().configureEach { dependsOn("compileAndroidMain") }
        tasks.withType<StabilityCheckTask>().configureEach { dependsOn("compileAndroidMain") }
    }
    extensions.configure<ComposeCompilerGradlePluginExtension> {
        fun Provider<String>.onlyIfTrue() = flatMap { provider { it.takeIf(String::toBoolean) } }

        fun Provider<*>.relativeToRootProject(dir: String) = map {
            isolated.rootProject.projectDirectory.dir("build").dir(projectDir.toRelativeString(rootDir))
        }
            .map { it.dir(dir) }
        project.providers
            .gradleProperty("enableComposeCompilerMetrics")
            .onlyIfTrue()
            .relativeToRootProject("compose-metrics")
            .let(metricsDestination::set)
        project.providers
            .gradleProperty("enableComposeCompilerReports")
            .onlyIfTrue()
            .relativeToRootProject("compose-reports")
            .let(reportsDestination::set)
        stabilityConfigurationFiles.add(stabilityConfig)
    }
}
