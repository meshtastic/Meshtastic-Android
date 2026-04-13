/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
    configurations.configureEach {
        exclude(mapOf("group" to "androidx.compose", "module" to "compose-bom"))
    }

    // CMP publishes core AndroidX groups at the CMP version tag (e.g. 1.11.0-beta02).
    // Material/Material3/Adaptive are intentionally excluded — CMP maps those to
    // different AndroidX version numbers (see release notes for the mapping table).
    val cmpVersion = libs.version("compose-multiplatform")
    val cmpAlignedGroups = setOf(
        "androidx.compose.animation",
        "androidx.compose.foundation",
        "androidx.compose.material",
        "androidx.compose.runtime",
        "androidx.compose.ui",
    )
    configurations.configureEach {
        resolutionStrategy.eachDependency {
            if (requested.group in cmpAlignedGroups) {
                useVersion(cmpVersion)
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
