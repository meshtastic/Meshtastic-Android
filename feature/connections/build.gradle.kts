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

plugins { alias(libs.plugins.meshtastic.kmp.feature) }

kotlin {
    android { withHostTest { isIncludeAndroidResources = true } }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    // nonWebMain: the adapters over core:datastore's Preferences-backed data sources, which have no wasmJs target.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyHierarchyTemplate(KotlinHierarchyTemplate.default) {
        common { group("nonWeb") { withCompilations { it.target.targetName != "wasmJs" } } }
    }

    // The predicate reaches only the iOS leaf compilations, not iosMain itself.
    sourceSets.getByName("iosMain") { dependsOn(sourceSets.getByName("nonWebMain")) }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.datastore)
            implementation(projects.core.di)
            implementation(projects.core.model)
            implementation(projects.core.navigation)
            implementation(libs.meshtastic.protobufs)
            implementation(projects.core.resources)
            implementation(projects.core.ui)
            implementation(projects.core.ble)
            implementation(projects.core.network)
        }

        androidMain.dependencies { implementation(libs.usb.serial.android) }

        // Compose UI tests live in jvmTest, not commonTest: this module enables android host tests, and the
        // androidHostTest stubs leave Build.FINGERPRINT null, which the Compose Robolectric idling strategy NPEs on.
        jvmTest.dependencies {
            implementation(libs.compose.multiplatform.ui.test)
            implementation(compose.desktop.currentOs)
        }

        // Tests that use core:testing, which has no wasmJs target; KmpFeatureConventionPlugin wires it here.
        getByName("nonWebTest") { dependsOn(commonTest.get()) }
    }
}
