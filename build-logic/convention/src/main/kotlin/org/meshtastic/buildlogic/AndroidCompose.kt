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

import com.android.build.api.dsl.CommonExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * Configure Compose-specific options
 */
internal fun Project.configureAndroidCompose(
    commonExtension: CommonExtension,
) {
    commonExtension.apply {
        buildFeatures.compose = true
    }

    dependencies {
        val bom = libs.library("androidx-compose-bom")
        "implementation"(platform(bom))
        "androidTestImplementation"(platform(bom))
        "implementation"(libs.library("androidx-compose-ui-tooling"))
        "implementation"(libs.library("androidx-compose-runtime"))
        "runtimeOnly"(libs.library("androidx-compose-runtime-tracing"))
        "debugImplementation"(libs.library("androidx-compose-ui-tooling"))

        // Add Espresso explicitly to avoid version mismatch issues on newer Android versions
        "androidTestImplementation"(libs.library("androidx-test-espresso-core"))
    }
    configureComposeCompiler()
}
