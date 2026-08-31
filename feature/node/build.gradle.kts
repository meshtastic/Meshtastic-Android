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
    alias(libs.plugins.meshtastic.kmp.feature)
    alias(libs.plugins.meshtastic.kotlinx.serialization)
}

kotlin {
    android { withHostTest { isIncludeAndroidResources = true } }

    // wasmJs { browser() } required repo-wide by KGP's root npm resolver — see core:prefs/build.gradle.kts's
    // comment for the full story (webApp's binaries.executable(), the wasmJsBrowserTest/karma gap it
    // exposed, and how that's now handled centrally).
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    // Bare default template, no custom group: empirically required (3 failed attempts otherwise —
    // typed iosMain accessor silently orphaned; same-block getByName("iosMain") hit a hard "not
    // found"; matching{} on the two iOS leaf mains still couldn't see commonMain's expects). Without
    // this explicit call, "iosMain"'s creation/wiring timing is unreliable once android
    // (com.android.kotlin.multiplatform.library) + wasmJs() are both registered with no template call
    // of its own — same KT-80409 territory core:ble/feature:connections hit, just surfacing without
    // any custom group needed here. Unlike those two modules' custom "nonWeb" group, the bare default
    // template already wires iosMain to appleMain correctly on its own — no extra edge needed (an
    // explicit iosMain-dependsOn-appleMain edge here only produced a harmless redundant-edge warning).
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyHierarchyTemplate(KotlinHierarchyTemplate.default)

    sourceSets {
        commonMain.dependencies {
            implementation(libs.coil)
            implementation(projects.core.common)
            implementation(projects.core.data)
            implementation(projects.core.database)
            implementation(projects.core.datastore)
            implementation(projects.core.domain)
            implementation(projects.core.model)
            implementation(projects.core.navigation)
            implementation(libs.meshtastic.protobufs)
            implementation(projects.core.repository)
            implementation(projects.core.resources)
            implementation(projects.core.service)
            implementation(projects.core.ui)
            implementation(projects.core.di)

            implementation(libs.markdown.renderer)
            implementation(libs.markdown.renderer.m3)
            implementation(libs.vico.compose)
            implementation(libs.vico.compose.m3)

            // JetBrains Material 3 Adaptive (multiplatform ListDetailPaneScaffold)
            implementation(libs.jetbrains.compose.material3.adaptive)
            implementation(libs.jetbrains.compose.material3.adaptive.layout)
            implementation(libs.jetbrains.compose.material3.adaptive.navigation)
            implementation(libs.jetbrains.compose.material3.adaptive.navigation3)
        }

        // feature:map has no wasmJs target of its own (map rendering is deferred, AC9). This module never
        // references it directly (only Koin's classpath-based ComponentScan("org.meshtastic.feature.map")
        // needs it on the classpath) — confirmed via grep for "import org.meshtastic.feature.map", zero
        // hits — so it's wired per-platform-main instead of commonMain, android/jvm/iOS only.
        androidMain.dependencies {
            implementation(libs.markdown.renderer.android)
            implementation(projects.feature.map)
        }
        jvmMain.dependencies { implementation(projects.feature.map) }
        getByName("iosMain").dependencies { implementation(projects.feature.map) }

        // Compose UI tests live in jvmTest, not commonTest: this module enables android host tests, and the
        // androidHostTest stubs leave Build.FINGERPRINT null, which the Compose Robolectric idling strategy NPEs on.
        jvmTest.dependencies {
            implementation(libs.compose.multiplatform.ui.test)
            implementation(compose.desktop.currentOs)
        }

        // TEST only: core:testing has no wasmJs target (same gap every other module this session hit).
        // 2 of 24 commonTest files depend on it (confirmed via grep for the import, not assumed) — moved
        // to a nonWebTest source set; the other 22 stay in commonTest and compile for wasmJs. core:testing
        // itself is wired into nonWebTest by KmpFeatureConventionPlugin (afterEvaluate, routes to
        // nonWebTest when present) — not added here.
        val nonWebTest by creating { dependsOn(commonTest.get()) }
        getByName("jvmTest") { dependsOn(nonWebTest) }
        getByName("androidHostTest") { dependsOn(nonWebTest) }
        matching { it.name == "iosArm64Test" || it.name == "iosSimulatorArm64Test" }
            .configureEach { dependsOn(nonWebTest) }
    }
}
