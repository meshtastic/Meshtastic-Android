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
    alias(libs.plugins.meshtastic.kmp.library)
    alias(libs.plugins.meshtastic.koin)
}

kotlin {
    android { withHostTest { isIncludeAndroidResources = true } }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser() }

    // nonWebMain: the last-BLE-node recorder writes through core:datastore's Preferences-backed data source.
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi::class)
    applyHierarchyTemplate(org.jetbrains.kotlin.gradle.plugin.KotlinHierarchyTemplate.default) {
        common { group("nonWeb") { withCompilations { it.target.targetName != "wasmJs" } } }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.core.repository)
            implementation(projects.core.common)
            implementation(projects.core.database)
            implementation(projects.core.datastore)
            implementation(projects.core.di)
            implementation(projects.core.model)
            implementation(projects.core.navigation)
            implementation(projects.core.network)
            implementation(projects.core.ble)
            implementation(projects.core.resources)
            implementation(libs.meshtastic.protobufs)

            implementation(libs.jetbrains.lifecycle.runtime)
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kermit)
        }

        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.work.runtime.ktx)
            implementation(libs.androidx.security.crypto)
            implementation(libs.koin.android)
            implementation(libs.koin.androidx.workmanager)
        }

        getByName("androidHostTest") {
            dependencies {
                implementation(projects.core.prefs)
                implementation(libs.androidx.datastore.preferences)
                implementation(libs.androidx.work.testing)
            }
        }

        // Tests that use core:testing, which has no wasmJs target.
        getByName("nonWebTest").dependencies { implementation(projects.core.testing) }
        getByName("androidHostTest") { dependsOn(getByName("nonWebTest")) }
    }
}
