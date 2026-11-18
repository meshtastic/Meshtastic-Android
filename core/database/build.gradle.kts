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

    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    // nonWebMain: sqlite-bundled and datastore-preferences have no wasmJs variant.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyHierarchyTemplate(KotlinHierarchyTemplate.default) {
        common { group("nonWeb") { withCompilations { it.target.targetName != "wasmJs" } } }
    }
    sourceSets.getByName("iosMain") { dependsOn(sourceSets.getByName("nonWebMain")) }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.okio)

            api(projects.core.common)
            implementation(projects.core.di)
            api(projects.core.model)
            implementation(libs.meshtastic.protobufs)
            implementation(libs.androidx.room.paging)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kermit)
        }
        getByName("nonWebMain").dependencies {
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.androidx.datastore.preferences)
        }
        wasmJsMain.dependencies {
            implementation(libs.androidx.sqlite.web)
            implementation(libs.kotlinx.browser)
            // The WebWorkerSQLiteDriver worker: sqlite-wasm over OPFS through the opfs-sahpool VFS.
            implementation(npm("sqlite-wasm-worker", file("worker")))
        }
        // core:testing has no wasmJs target.
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
    // Without it the klib compiles and fails at runtime with IrLinkageError on the generated constructor actual.
    "kspWasmJs"(libs.androidx.room.compiler)
}
