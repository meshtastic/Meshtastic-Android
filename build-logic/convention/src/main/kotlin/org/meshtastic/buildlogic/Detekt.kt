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
import dev.detekt.gradle.extensions.DetektExtension
import dev.detekt.gradle.extensions.FailOnSeverity
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType
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

    // Default sources. Every production source set that ships code must be listed explicitly — detekt silently
    // skips anything not named here, which is how src/fdroid, src/google, src/iosMain, and src/jvmAndroidMain
    // went unanalyzed for as long as only the main/common sets were listed. (Test source sets are deliberately
    // not scanned, matching the original list.)
    source.setFrom(
        files(
            "src/main/java",
            "src/main/kotlin",
            "src/commonMain/kotlin",
            "src/androidMain/kotlin",
            "src/jvmMain/kotlin",
            "src/jvmAndroidMain/kotlin",
            "src/iosMain/kotlin",
            "src/fdroid/java",
            "src/fdroid/kotlin",
            "src/google/java",
            "src/google/kotlin",
        ),
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
        // Filters by name only, so the rest of the project's tasks stay unrealized.
        typeResolved.configure { dependsOn(tasks.named { name -> name == taskName }) }
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
        }
    }
}
