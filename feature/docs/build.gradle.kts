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
    id("meshtastic.kmp.jvm.android")
}

kotlin {
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

            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.jetbrains.navigation3.ui)
            implementation(libs.markdown.renderer)
            implementation(libs.markdown.renderer.m3)
        }

        commonTest.dependencies { implementation(libs.compose.multiplatform.ui.test) }

        jvmTest.dependencies { implementation(compose.desktop.currentOs) }
    }
}

/**
 * Sync task: copies markdown docs from the canonical `docs/` source directory into the CMP composeResources location.
 * This eliminates manual content duplication and ensures the in-app bundled docs always reflect the source-of-truth.
 *
 * Usage: ./gradlew :feature:docs:syncDocsToComposeResources
 *
 * This task runs automatically before resource generation tasks.
 */
val syncDocsToComposeResources by
    tasks.registering(Sync::class) {
        description = "Syncs docs/ markdown source into composeResources for in-app bundling"
        group = "docs"

        val docsSourceDir = rootProject.layout.projectDirectory.dir("docs")
        val composeResourcesTarget = layout.projectDirectory.dir("src/commonMain/composeResources/files/docs")

        from(docsSourceDir) {
            include("user/**/*.md")
            include("developer/**/*.md")
            // Exclude Jekyll/site-only files that are not needed in-app
            exclude("_config.yml")
            exclude("_data/**")
            exclude("_includes/**")
            exclude("_layouts/**")
            exclude("index.md")
            exclude("assets/**")
            exclude("Gemfile*")
        }
        into(composeResourcesTarget)

        // Preserve timestamps for up-to-date checks
        preserve { include("**/.gitkeep") }
    }

// Wire sync task to run before any resource generation
tasks
    .matching { it.name.contains("generateComposeResClass") || it.name.contains("copyNonXmlValueResources") }
    .configureEach { dependsOn(syncDocsToComposeResources) }
