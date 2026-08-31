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

import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate

plugins {
    alias(libs.plugins.meshtastic.kmp.library)
    alias(libs.plugins.meshtastic.kmp.library.compose)
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs()

    android {
        androidResources {
            enable = true
            resourcePrefix = "meshtastic_"
        }
        withHostTest { isIncludeAndroidResources = true }
    }

    // runBlocking has no wasmJs implementation; getString()'s blocking overloads live in nonWebMain
    // instead of commonMain (same pattern as core:ble -- see its build.gradle.kts for why the predicate
    // form + explicit iosMain edge, not withAndroidTarget()/withApple()).
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyHierarchyTemplate(KotlinHierarchyTemplate.default) {
        common { group("nonWeb") { withCompilations { it.target.targetName != "wasmJs" } } }
    }
    sourceSets.getByName("iosMain") { dependsOn(sourceSets.getByName("nonWebMain")) }

    sourceSets {
        commonMain.dependencies { implementation(projects.core.common) }
        getByName("nonWebMain").dependencies { implementation(libs.kotlinx.coroutines.core) }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "org.meshtastic.core.resources"
}
