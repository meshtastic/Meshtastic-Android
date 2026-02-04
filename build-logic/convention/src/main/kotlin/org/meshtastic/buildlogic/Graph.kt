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

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity.NONE
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.meshtastic.buildlogic.PluginType.Unknown
import kotlin.text.RegexOption.DOT_MATCHES_ALL

/**
 * Declaration order is important, as only the first match will be retained.
 */
internal enum class PluginType(val id: String, val ref: String, val style: String) {
    AndroidApplication(
        id = "meshtastic.android.application",
        ref = "android-application",
        style = "fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000",
    ),
    AndroidApplicationCompose(
        id = "meshtastic.android.application.compose",
        ref = "android-application-compose",
        style = "fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000",
    ),
    AndroidFeature(
        id = "meshtastic.android.feature",
        ref = "android-feature",
        style = "fill:#FFD6A5,stroke:#000,stroke-width:2px,color:#000",
    ),
    AndroidLibrary(
        id = "meshtastic.android.library",
        ref = "android-library",
        style = "fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000",
    ),
    AndroidLibraryCompose(
        id = "meshtastic.android.library.compose",
        ref = "android-library-compose",
        style = "fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000",
    ),
    AndroidTest(
        id = "meshtastic.android.test",
        ref = "android-test",
        style = "fill:#A0C4FF,stroke:#000,stroke-width:2px,color:#000",
    ),
    Jvm(
        id = "meshtastic.jvm.library",
        ref = "jvm-library",
        style = "fill:#BDB2FF,stroke:#000,stroke-width:2px,color:#000",
    ),
    KmpLibrary(
        id = "meshtastic.kmp.library",
        ref = "kmp-library",
        style = "fill:#FFC1CC,stroke:#000,stroke-width:2px,color:#000",
    ),
    Unknown(
        id = "?",
        ref = "unknown",
        style = "fill:#FFADAD,stroke:#000,stroke-width:2px,color:#000",
    ),
}

/**
 * Optimized and Isolated Projects compatible graph configuration.
 */
internal fun Project.configureGraphTasks() {
    if (!buildFile.exists()) return

    val supportedConfigurations = providers.gradleProperty("graph.supportedConfigurations")
        .map { it.split(",").toSet() }
        .orElse(setOf("api", "implementation", "baselineProfile", "testedApks"))

    val dumpTask = tasks.register<GraphDumpTask>("graphDump") {
        projectPath.set(this@configureGraphTasks.path)
        
        val deps = mutableMapOf<String, Set<Pair<String, String>>>()
        val projectPlugins = mutableMapOf<String, PluginType>()
        
        projectPlugins[path] = PluginType.entries.firstOrNull { pluginManager.hasPlugin(it.id) } ?: Unknown
        
        val projectDeps = mutableSetOf<Pair<String, String>>()
        this@configureGraphTasks.configurations.forEach { config ->
            if (config.name in supportedConfigurations.get()) {
                config.dependencies.withType<ProjectDependency>().forEach { dep ->
                    // Fallback to simpler access or path if available.
                    val depPath = dep.path
                    projectDeps.add(config.name to depPath)
                }
            }
        }
        deps[path] = projectDeps
        
        dependenciesData.set(deps)
        pluginsData.set(projectPlugins)
        output.set(layout.buildDirectory.file("mermaid/graph.txt"))
        legend.set(layout.buildDirectory.file("mermaid/legend.txt"))
    }

    tasks.register<GraphUpdateTask>("graphUpdate") {
        projectPath.set(this@configureGraphTasks.path)
        input.set(dumpTask.flatMap { it.output })
        legend.set(dumpTask.flatMap { it.legend })
        output.set(layout.projectDirectory.file("README.md"))
    }
}

@CacheableTask
private abstract class GraphDumpTask : DefaultTask() {

    @get:Input
    abstract val projectPath: Property<String>

    @get:Input
    abstract val dependenciesData: MapProperty<String, Set<Pair<String, String>>>

    @get:Input
    abstract val pluginsData: MapProperty<String, PluginType>

    @get:OutputFile
    abstract val output: RegularFileProperty

    @get:OutputFile
    abstract val legend: RegularFileProperty

    @TaskAction
    operator fun invoke() {
        output.get().asFile.writeText(mermaid())
        legend.get().asFile.writeText(legend())
    }

    private fun mermaid() = buildString {
        appendLine("graph TB")
        val currentProject = projectPath.get()
        val projectPlugins = pluginsData.get()
        val projectDeps = dependenciesData.get()[currentProject] ?: emptySet()
        
        appendLine("  $currentProject[${currentProject.substringAfterLast(":")}]:::${projectPlugins[currentProject]?.ref}")
        
        projectDeps.forEach { (config, depPath) ->
            val link = when (config) {
                "api" -> "-->"
                else -> "-.->"
            }
            appendLine("  $currentProject $link $depPath")
        }
        
        appendLine()
        PluginType.entries.forEach { appendLine("classDef ${it.ref} ${it.style};") }
    }

    private fun legend() = buildString {
        appendLine("graph TB")
        appendLine("  subgraph Legend")
        appendLine("    direction TB")
        appendLine("    L1[Application]:::android-application")
        appendLine("    L2[Library]:::android-library")
        appendLine("  end")
        PluginType.entries.forEach { appendLine("classDef ${it.ref} ${it.style};") }
    }
}

@CacheableTask
private abstract class GraphUpdateTask : DefaultTask() {
    @get:Input
    abstract val projectPath: Property<String>
    @get:InputFile
    @get:PathSensitive(NONE)
    abstract val input: RegularFileProperty
    @get:InputFile
    @get:PathSensitive(NONE)
    abstract val legend: RegularFileProperty
    @get:OutputFile
    abstract val output: RegularFileProperty

    @TaskAction
    fun update() {
        val readme = output.get().asFile
        if (!readme.exists()) return
        val mermaid = input.get().asFile.readText()
        // Update logic...
        readme.writeText(readme.readText().replace(Regex("<!--region graph-->.*?<!--endregion-->", DOT_MATCHES_ALL), "<!--region graph-->\n```mermaid\n$mermaid\n```\n<!--endregion-->"))
    }
}
