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
import com.android.build.api.dsl.LibraryExtension
import org.gradle.testretry.TestRetryTaskExtension

plugins {
    alias(libs.plugins.meshtastic.android.library)
    alias(libs.plugins.meshtastic.android.library.compose)
    alias(libs.plugins.compose.screenshot)
}

configure<LibraryExtension> {
    namespace = "org.meshtastic.screenshot.tests"

    experimentalProperties["android.experimental.enableScreenshotTest"] = true

    testOptions { screenshotTests { imageDifferenceThreshold = 0.0005f } }
}

// CST screenshot tests use a custom runner incompatible with test-retry
tasks.withType<Test>().configureEach {
    if (name.contains("ScreenshotTest", ignoreCase = true)) {
        extensions.configure<TestRetryTaskExtension> { maxRetries.set(0) }
    }
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:resources"))
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":feature:messaging"))
    implementation(project(":feature:node"))
    implementation(project(":feature:wifi-provision"))
    implementation(project(":feature:connections"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:firmware"))
    implementation(project(":feature:intro"))
    implementation(project(":feature:map"))
    implementation(project(":feature:docs"))

    implementation(libs.compose.multiplatform.foundation)
    implementation(libs.compose.multiplatform.material3)
    implementation(libs.compose.multiplatform.runtime)
    implementation(libs.compose.multiplatform.ui)

    screenshotTestImplementation(libs.screenshot.validation.api)
}

tasks.register<Copy>("copyDocsScreenshots") {
    description = "Copies selected reference screenshots to docs/screenshots/ for the docs pipeline."
    group = "documentation"

    val referenceDir = layout.projectDirectory.dir("src/screenshotTestDebug/reference")
    val manifestFile = layout.projectDirectory.file("docs-screenshots-manifest.txt")
    val aliasFile = layout.projectDirectory.file("docs-screenshot-aliases.properties")
    val outputDir = rootProject.layout.projectDirectory.dir("docs/screenshots")

    // Read manifest patterns at configuration time so Copy task can resolve includes
    val manifestPatterns =
        manifestFile.asFile.let { file ->
            if (file.exists()) {
                file.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            } else {
                emptyList()
            }
        }

    from(referenceDir) { include(manifestPatterns) }
    into(outputDir)

    // Build reverse alias map (CST name → semantic name) for renaming during copy.
    val reverseAliases: Map<String, String> by lazy {
        val file = aliasFile.asFile
        if (!file.exists()) return@lazy emptyMap()
        file
            .readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
            .associate { line ->
                val (semantic, cst) = line.split('=', limit = 2)
                cst.trim() to semantic.trim()
            }
    }

    // Flatten directory structure and apply alias renaming
    eachFile {
        val alias = reverseAliases[name]
        path = alias ?: name
    }
    duplicatesStrategy = DuplicatesStrategy.WARN
    includeEmptyDirs = false

    doFirst {
        val refDir = referenceDir.asFile
        require(refDir.exists()) {
            "Reference screenshot directory not found: ${refDir.absolutePath}. " +
                "Run :screenshot-tests:updateDebugScreenshotTest first."
        }
        if (manifestPatterns.isEmpty()) {
            logger.warn("Screenshot manifest is empty — no files will be copied.")
        }
    }

    doLast {
        val copiedFiles = outputDir.asFile.listFiles()?.filter { it.isFile && it.extension == "png" } ?: emptyList()
        if (copiedFiles.isEmpty()) {
            logger.warn(
                "copyDocsScreenshots: manifest patterns matched no files in ${referenceDir.asFile.absolutePath}. " +
                    "Check pattern spelling in ${manifestFile.asFile.name}.",
            )
        } else {
            logger.lifecycle("copyDocsScreenshots: copied ${copiedFiles.size} screenshots to ${outputDir.asFile.path}")
        }
    }
}
