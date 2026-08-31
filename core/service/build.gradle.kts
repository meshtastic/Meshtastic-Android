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

    // Library module: bare wasmJs(), no browser() — see core:prefs/build.gradle.kts's comment. No custom
    // hierarchy group is needed for MAIN: core:takserver (the one dependency with no wasmJs variant) is
    // removed entirely below, not relocated, and nothing else in commonMain/androidMain/jvmMain is
    // web-hostile — same shape as core:repository, unlike core:ble/core:database/core:prefs/core:network.
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs()

    sourceSets {
        commonMain.dependencies {
            api(projects.core.repository)
            implementation(projects.core.common)
            implementation(projects.core.data)
            implementation(projects.core.database)
            implementation(projects.core.di)
            implementation(projects.core.model)
            implementation(projects.core.navigation)
            implementation(projects.core.network)
            implementation(projects.core.ble)
            implementation(projects.core.prefs)
            implementation(projects.core.resources)
            implementation(libs.meshtastic.protobufs)
            // core:takserver removed (was only used by MeshServiceOrchestrator, which now depends on
            // core:repository's TakServerIntegration seam instead — see TakServerIntegration.kt).
            // Confirmed via grep: nothing else in this module references org.meshtastic.core.takserver.

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
                implementation(libs.androidx.datastore.preferences)
                implementation(libs.androidx.work.testing)
            }
        }

        // TEST only: three of this module's six commonTest files don't compile for wasmJs, for two
        // unrelated reasons, both confirmed empirically via a real compileTestKotlinWasmJs run (never
        // guessed): (1) SharedRadioInterfaceServiceLivenessTest.kt depends on core:testing, which has no
        // wasmJs target (same gap core:ble/core:database/core:network/core:repository each hit).
        // (2) RadioControllerImplTest.kt and RadioControllerRestoreTest.kt both crash the Kotlin/Wasm
        // compiler ("Serialization of IrErrorType is not supported anymore") -- bisected to exactly these
        // two files (the other four compile fine); the one thing both do that no passing file does is
        // construct a real RadioControllerImpl, which delegates to four collaborators via `by` (interface
        // delegation) alongside Lazy<T>/default-value constructor params. That combination is the leading
        // suspect, not confirmed as the exact trigger -- this looks like a genuine Kotlin/Wasm backend
        // limitation, not a missing library, and is out of this module's scope to fix. A plain additional
        // source set -- not a full applyHierarchyTemplate reset -- keeps this scoped to test only, since
        // MAIN needs no split at all (unlike core:ble/core:database/core:prefs/core:network).
        //
        // jvmTest/androidHostTest are leaf source sets tied 1:1 to their registered target, created
        // synchronously and so already exist here — but the shared "iosTest" intermediate the default
        // hierarchy template would otherwise provide is NOT materialized this early (its creation is
        // deferred; confirmed empirically: `getByName("iosTest")` here throws "KotlinSourceSet with name
        // 'iosTest' not found", while jvmTest/androidHostTest resolve fine). Wire the two iOS leaf test
        // source sets directly instead.
        val nonWebTest by creating {
            dependsOn(commonTest.get())
            dependencies { implementation(projects.core.testing) }
        }
        getByName("jvmTest") { dependsOn(nonWebTest) }
        getByName("androidHostTest") { dependsOn(nonWebTest) }
        matching { it.name == "iosArm64Test" || it.name == "iosSimulatorArm64Test" }
            .configureEach { dependsOn(nonWebTest) }
    }
}
