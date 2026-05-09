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
    val outputDir = rootProject.layout.projectDirectory.dir("docs/screenshots")

    from(referenceDir)
    into(outputDir)

    // Flatten directory structure: just the filename
    eachFile { path = name }
    duplicatesStrategy = DuplicatesStrategy.FAIL
    includeEmptyDirs = false

    doFirst {
        val refDir = referenceDir.asFile
        require(refDir.exists()) {
            "Reference screenshot directory not found: ${refDir.absolutePath}. " +
                "Run :screenshot-tests:updateDebugScreenshotTest first."
        }
        val file = manifestFile.asFile
        require(file.exists()) {
            "Screenshot manifest not found: ${file.absolutePath}. " +
                "This file lists which reference screenshots to copy for the docs pipeline."
        }
        val patterns = file.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        if (patterns.isEmpty()) {
            logger.warn("Screenshot manifest is empty — no files will be copied: ${file.absolutePath}")
        }
        include(patterns)
    }

    doLast {
        val copied = outputs.files.files.filter { it.isFile }
        if (copied.isEmpty()) {
            logger.warn(
                "copyDocsScreenshots: manifest patterns matched no files in ${referenceDir.asFile.absolutePath}. " +
                    "Check pattern spelling in ${manifestFile.asFile.name}.",
            )
        }
    }
}
