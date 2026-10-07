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

import com.android.build.api.variant.AndroidComponentsExtension
import dev.detekt.gradle.Detekt
import dev.detekt.gradle.DetektCreateBaselineTask
import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.extensions.FailOnSeverity
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import java.io.File

internal fun Project.configureDetekt(extension: DetektExtension) = extension.apply {
    toolVersion.set(libs.version("detekt"))
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    buildUponDefaultConfig.set(true)
    allRules.set(false)
    parallel.set(true)
    basePath.set(rootDir)
    failOnSeverity.set(FailOnSeverity.Error)

    // Use per-module baseline files to suppress pre-existing violations introduced by the
    // detekt 2.0 upgrade. New violations will still be caught. Remove baselines incrementally
    // as modules are cleaned up.
    val baselineFile = project.file("detekt-baseline.xml")
    if (baselineFile.exists()) {
        baseline.set(baselineFile)
    }

    // Every source set under src/ (main, *Main, flavors, build types), so a new one is analysed without being
    // listed here. Test source sets (test*, *Test*) are skipped.
    source.setFrom(
        fileTree("src") {
            include("*/kotlin/**", "*/java/**")
            exclude("test*/**", "*Test*/**")
        },
    )

    // Type-resolved tasks take their sources from the compilation, which includes generated code under build/.
    val buildDirPrefix = layout.buildDirectory.get().asFile.absolutePath + File.separator
    tasks.withType<Detekt>().configureEach {
        val isCi = project.findProperty("ci") == "true"
        // One baseline per module for every task: detekt would otherwise prefer a detekt-baseline-<compilation>.xml.
        if (baselineFile.exists()) baseline.set(baselineFile)
        exclude { it.file.absolutePath.startsWith(buildDirPrefix) }
        // Named per task: plain and type-resolved tasks run in the same build and must not share report files.
        val reportName = name
        reports {
            checkstyle {
                required.set(true)
                outputLocation.set(layout.buildDirectory.file("reports/detekt/$reportName.xml"))
            }
            sarif {
                required.set(true)
                outputLocation.set(layout.buildDirectory.file("reports/detekt/$reportName.sarif"))
            }
            // In CI, only generate checkstyle and sarif (needed for GitHub reporting).
            // Skip html and markdown to save processing time.
            html {
                required.set(!isCi)
                outputLocation.set(layout.buildDirectory.file("reports/detekt/$reportName.html"))
            }
            markdown {
                required.set(!isCi)
                outputLocation.set(layout.buildDirectory.file("reports/detekt/$reportName.md"))
            }
        }
    }
    registerTypeResolvedDetekt()
    dependencies {
        "detektPlugins"(libs.library("detekt-formatting"))
        "detektPlugins"(libs.library("detekt-compose"))
    }
}

/**
 * Registers `detektTypeResolved`, which runs detekt with the production classpath of each JVM and Android debug
 * compilation. Plain `detekt` has no classpath, so every rule that needs type resolution is skipped there.
 */
private fun Project.registerTypeResolvedDetekt() {
    val typeResolved =
        tasks.register("detektTypeResolved") {
            group = "verification"
            description = "Runs detekt with type resolution on the production JVM and Android debug compilations"
        }

    fun include(suffix: String) {
        val taskName = "detekt${suffix.replaceFirstChar { char -> char.uppercase() }}"
        // Matches against the registered names: iterating a filtered task collection realizes every pending task,
        // including ones that throw on creation.
        typeResolved.configure { dependsOn(provider { tasks.names.filter { name -> name == taskName } }) }
    }

    plugins.withId("org.jetbrains.kotlin.multiplatform") {
        extensions
            .getByType<KotlinMultiplatformExtension>()
            .targets
            .matching { target ->
                target.platformType == KotlinPlatformType.jvm || target.platformType == KotlinPlatformType.androidJvm
            }
            .configureEach { include("main${name.replaceFirstChar { char -> char.uppercase() }}") }
    }
    plugins.withId("org.jetbrains.kotlin.jvm") { include("main") }
    listOf("com.android.application", "com.android.library").forEach { pluginId ->
        plugins.withId(pluginId) {
            val components = extensions.getByType(AndroidComponentsExtension::class.java)
            components.onVariants(components.selector().withBuildType("debug")) { variant -> include(variant.name) }
            components.onVariants { variant -> addJavacClassesToDetektClasspath(variant.name) }
        }
    }
}

/**
 * Generated Java such as `BuildConfig` reaches the analysis only as javac's classes, which the plugin leaves off the
 * classpath. Its classpath is a convention set after this action runs, so the whole value is set here instead.
 */
private fun Project.addJavacClassesToDetektClasspath(variantName: String) {
    val suffix = variantName.replaceFirstChar { char -> char.uppercase() }
    fun variantClasspath() = listOf(
        tasks.named<KotlinJvmCompile>("compile${suffix}Kotlin").map { task -> task.libraries },
        tasks.named<JavaCompile>("compile${suffix}JavaWithJavac").flatMap { task -> task.destinationDirectory },
    )
    tasks
        .withType<Detekt>()
        .named { name -> name == "detekt$suffix" }
        .configureEach {
            classpath.setFrom(variantClasspath())
        }
    tasks
        .withType<DetektCreateBaselineTask>()
        .named { name -> name == "detektBaseline$suffix" }
        .configureEach {
            classpath.setFrom(variantClasspath())
        }
}
