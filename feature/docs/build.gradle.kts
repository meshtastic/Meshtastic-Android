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

plugins {
    alias(libs.plugins.meshtastic.kmp.feature)
    alias(libs.plugins.meshtastic.kotlinx.serialization)
}

kotlin {
    // No withHostTest: commonTest holds Compose UI tests, which NPE on the host-test stubs' null Build.FINGERPRINT.
    android {
        namespace = "org.meshtastic.feature.docs"
        androidResources.enable = true
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.navigation)
            implementation(projects.core.resources)
            implementation(projects.core.ui)
            implementation(projects.core.di)

            implementation(libs.jetbrains.compose.material3.adaptive)
            implementation(libs.jetbrains.compose.material3.adaptive.navigation3)
            implementation(libs.coil)
            implementation(libs.markdown.renderer)
            implementation(libs.markdown.renderer.m3)
        }

        commonTest.dependencies { implementation(libs.compose.multiplatform.ui.test) }

        jvmTest.dependencies { implementation(compose.desktop.currentOs) }
    }
}

/**
 * Builds this module's whole commonMain compose-resources tree from the canonical `docs/` directory: English pages and
 * the shared screenshots under `files/docs/`, Crowdin translations under `files/{locale}/docs/` (CMP has no qualifiers
 * on `files/`, so the locale is a directory).
 */
val docsComposeResources = layout.buildDirectory.dir("generated/docsComposeResources")

val syncDocsToComposeResources =
    tasks.register<Sync>("syncDocsToComposeResources") {
        description = "Bundles docs/ markdown, screenshots and translations as compose resources for the in-app reader."
        group = "docs"

        // isolated.rootProject: Isolated Projects forbids reaching through rootProject.layout.
        val docsDir = isolated.rootProject.projectDirectory.dir("docs")

        from(docsDir.dir("en")) {
            include("user/**/*.md")
            include("developer/**/*.md")
            into("files/docs")
        }

        // FR-038: docs/assets/screenshots/ is the tracked screenshot set shared with the Jekyll site, refreshed from
        // the CST references by copyDocsScreenshots, so the site and the in-app reader serve identical images.
        from(docsDir.dir("assets/screenshots")) {
            include("**/*.png")
            into("files/docs/assets/screenshots")
        }

        // Crowdin names these like the values-* dirs (fr, pt-rBR, b+sr+Latn).
        from(docsDir) {
            include("*/user/**/*.md")
            exclude("en/**")
            exclude("_*/**")
            exclude("assets/**")
            exclude("screenshots/**")
            eachFile {
                path = "files/${relativePath.segments[0]}/docs/${relativePath.segments.drop(1).joinToString("/")}"
            }
        }

        includeEmptyDirs = false
        into(docsComposeResources)
    }

// Mapping the task provider carries the task dependency into every compose-resources task that reads the directory.
compose.resources { customDirectory("commonMain", syncDocsToComposeResources.map { docsComposeResources.get() }) }
