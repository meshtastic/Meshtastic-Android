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

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Configure Compose-specific options */
internal fun Project.configureAndroidCompose(commonExtension: CommonExtension) {
    commonExtension.apply { buildFeatures.compose = true }

    // CMP is the sole Compose version authority (BOM removed from the catalog).
    // Third-party libraries (maps-compose, datadog, etc.) carry a transitive
    // compose-bom whose constraints conflict with CMP-published AndroidX artifacts.
    // Exclude it globally so CMP's own dependency graph wins.
    configurations.configureEach { exclude(mapOf("group" to "androidx.compose", "module" to "compose-bom")) }

    // CMP publishes these core AndroidX groups at an AndroidX version tag that
    // tracks (but does not equal) the CMP version. The exact mapping lives in
    // the CMP release notes; we mirror it via the `androidx-compose-bom-aligned`
    // version ref in libs.versions.toml. Material, Material3, and Adaptive follow
    // separate AndroidX version numbers and must NOT be included here.
    val androidxComposeVersion = libs.version("androidx-compose-bom-aligned")
    val cmpAlignedGroups =
        setOf(
            "androidx.compose.animation",
            "androidx.compose.foundation",
            "androidx.compose.runtime",
            "androidx.compose.ui",
        )
    // `androidx.compose.material:material` is ALSO requested version-less by maps-compose-widgets,
    // but it is pinned via an explicit versioned dependency in :androidApp rather than forced here —
    // a force does not cross the project boundary, so consumers of the app's graph (e.g.
    // :baselineprofile) would otherwise re-orphan it. Do not re-add a force for it here.
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group in cmpAlignedGroups) {
                useVersion(androidxComposeVersion)
            }
        }
    }

    val hasAndroidTest = project.projectDir.resolve("src/androidTest").exists()
    dependencies {
        "debugImplementation"(libs.library("compose-multiplatform-ui-tooling"))
        "implementation"(libs.library("compose-multiplatform-runtime"))
        "runtimeOnly"(libs.library("androidx-compose-runtime-tracing"))

        "implementation"(libs.library("compose-multiplatform-resources"))

        // Add Espresso explicitly to avoid version mismatch issues on newer Android versions
        if (hasAndroidTest) {
            "androidTestImplementation"(libs.library("androidx-test-espresso-core"))
        }
    }
    configureComposeCompiler()
}
