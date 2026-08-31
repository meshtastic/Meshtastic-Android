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
    alias(libs.plugins.meshtastic.android.room)
    alias(libs.plugins.meshtastic.kotlinx.serialization)
    alias(libs.plugins.meshtastic.koin)
}

kotlin {
    android {
        withHostTest { isIncludeAndroidResources = true }
        withDeviceTest { instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    }

    // Library module: bare wasmJs(), no browser() (that's for the eventual webApp executable).
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs()

    // nonWebMain: androidx.sqlite:sqlite-bundled and androidx.datastore:datastore-preferences have no wasmJs
    // variant (the `Preferences` type itself doesn't resolve there), so DatabaseManager/DatabaseDataStore and
    // everything Preferences-shaped live here instead of commonMain. Predicate, not
    // withAndroidTarget()/withApple() — those silently drop androidMain under
    // com.android.kotlin.multiplatform.library (KT-80409). See core/ble/build.gradle.kts for the same pattern.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyHierarchyTemplate(KotlinHierarchyTemplate.default) {
        common { group("nonWeb") { withCompilations { it.target.targetName != "wasmJs" } } }
    }

    // The predicate above misses iosMain itself (only reaches the two leaf iOS compilations), so any
    // nonWebMain-only actual can't see nonWebMain's expects/types without this explicit edge.
    sourceSets.getByName("iosMain") { dependsOn(sourceSets.getByName("nonWebMain")) }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.okio)

            api(projects.core.common)
            implementation(projects.core.di)
            api(projects.core.model)
            implementation(libs.meshtastic.protobufs)
            implementation(projects.core.resources)
            implementation(libs.androidx.room.paging)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kermit)
        }

        // android/jvm/ios only (see hierarchy template above) — sqlite-bundled and datastore-preferences have
        // no wasmJs variant, and DatabaseManager (the sole consumer of both) lives here.
        getByName("nonWebMain").dependencies {
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.androidx.datastore.preferences)
        }

        wasmJsMain.dependencies {
            implementation(libs.androidx.sqlite.web)
            implementation(libs.kotlinx.browser)
            // Generic WebWorkerSQLiteDriver worker script (open/prepare/step/close over @sqlite.org/sqlite-wasm
            // + OPFS) — same shape as the proven danysantiago/room-web-demo reference, vendored locally since
            // this module doesn't need a second Gradle module just for the worker.
            implementation(npm("sqlite-wasm-worker", file("worker")))
        }

        // kotlin("test") only — previously came transitively through core:testing's `api(kotlin("test"))`, which
        // commonTest can no longer depend on directly (see nonWebTest below). The few commonTest files left after
        // that split (BuildDbNameTest, ConvertersTest, DbFlowRecoveryTest, DeviceIdentityTest, ReactionKeyTest)
        // still need it, and wasmJs needs its own resolvable copy too.
        commonTest.dependencies { implementation(kotlin("test")) }

        // Everything that needs a real driver (getInMemoryDatabaseBuilder(), core:testing's setupTestContext()) or
        // core:testing itself lives in nonWebTest, not commonTest: core:testing has no wasmJs target of its own
        // (it in turn depends on core:repository/core:datastore, neither of which does either — the same dependency
        // -chain problem core:ble hit, not something worth solving just to satisfy this module's tests), and
        // getInMemoryDatabaseBuilder() has no real in-memory equivalent over OPFS on wasmJs (see its wasmJs actual).
        getByName("nonWebTest").dependencies {
            implementation(projects.core.testing)
            implementation(libs.androidx.room.testing)
        }

        getByName("androidHostTest") {
            dependencies {
                implementation(libs.androidx.sqlite.bundled)
                runtimeOnly(libs.androidx.sqlite.bundled.jvm)
                implementation(libs.androidx.room.testing)
                implementation(libs.junit)
            }
        }
        getByName("androidDeviceTest") {
            dependencies {
                implementation(libs.androidx.room.testing)
                implementation(libs.androidx.test.ext.junit)
                implementation(libs.androidx.test.runner)
            }
        }
    }
}

dependencies {
    "kspJvm"(libs.androidx.room.compiler)
    "kspJvmTest"(libs.androidx.room.compiler)
    // KSP resolves this via a detached configuration at task execution time,
    // so we declare it explicitly to ensure offline/Flatpak builds can resolve it.
    "kspJvm"(libs.ksp.symbol.processing.aa.embeddable)
    "kspAndroidHostTest"(libs.androidx.room.compiler)
    "kspAndroidDeviceTest"(libs.androidx.room.compiler)
    // Module-local wasmJs wiring: AndroidRoomConventionPlugin only adds kspAndroid/kspJvm (it's applied by this
    // module alone in the whole repo), so wire kspWasmJs here rather than teaching the shared plugin about a
    // target no other Room consumer has.
    "kspWasmJs"(libs.androidx.room.compiler)
}
