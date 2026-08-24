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
    alias(libs.plugins.meshtastic.android.screenshot)
}

configure<LibraryExtension> {
    namespace = "org.meshtastic.screenshot.tests"

    testOptions { screenshotTests { imageDifferenceThreshold = 0.0005f } }
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.resources)
    implementation(projects.core.model)
    implementation(projects.core.common)
    implementation(projects.feature.messaging)
    implementation(projects.feature.node)
    implementation(projects.feature.wifiProvision)
    implementation(projects.feature.connections)
    implementation(projects.feature.settings)
    implementation(projects.feature.intro)
    implementation(projects.feature.map)
    implementation(projects.feature.docs)
    implementation(projects.feature.discovery)
}

tasks.register<Copy>("copyDocsScreenshots") {
    description =
        "Refreshes the semantically-named docs screenshots in docs/assets/screenshots/ from CST reference images."
    group = "documentation"

    val referenceDir = layout.projectDirectory.dir("src/screenshotTestDebug/reference")
    // Doc-framed compositions live in the generate-only :docs-screenshots module; aggregate its references too.
    val docsReferenceDir =
        isolated.rootProject.projectDirectory.dir("docs-screenshots/src/screenshotTestDebug/reference")
    val manifestFile = layout.projectDirectory.file("docs-screenshots-manifest.txt")
    val aliasFile = layout.projectDirectory.file("docs-screenshot-aliases.properties")
    val outputDir = isolated.rootProject.projectDirectory.dir("docs/assets/screenshots")

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
    from(docsReferenceDir) { include(manifestPatterns) }
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

    // Flatten directory structure, keep only screenshots with a semantic alias, and rename them.
    // Unaliased reference images are skipped: only curated, semantically-named screenshots feed the
    // docs site and the in-app bundle.
    eachFile {
        val alias = reverseAliases[name]
        if (alias == null) {
            exclude()
        } else {
            path = alias
        }
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
