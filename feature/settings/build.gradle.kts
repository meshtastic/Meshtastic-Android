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
    // No withHostTest: commonTest holds Compose UI tests, which NPE on the host-test stubs' null Build.FINGERPRINT.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    // nonWebMain holds the TAK screens: core:takserver binds a TLS server socket, which a browser cannot. jvmAndroid
    // nests inside nonWeb because a project takes one hierarchy template, so meshtastic.kmp.jvm.android is not applied.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyHierarchyTemplate(KotlinHierarchyTemplate.default) {
        common {
            group("nonWeb") {
                withCompilations { it.target.targetName != "wasmJs" }
                group("jvmAndroid") {
                    withCompilations { it.target.targetName == "android" || it.target.targetName == "jvm" }
                }
            }
        }
    }
    sourceSets.getByName("iosMain") { dependsOn(sourceSets.getByName("nonWebMain")) }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.database)
            implementation(projects.core.domain)
            implementation(projects.core.model)
            implementation(projects.core.navigation)
            implementation(projects.core.network)
            implementation(libs.meshtastic.protobufs)
            implementation(projects.core.repository)
            implementation(projects.core.resources)
            implementation(projects.core.ui)
            implementation(projects.core.di)

            implementation(libs.kotlinx.atomicfu)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.aboutlibraries.compose.m3)
            implementation(libs.coil)
        }

        getByName("nonWebMain").dependencies { implementation(projects.core.takserver) }

        androidMain.dependencies { implementation(libs.androidx.appcompat) }

        commonTest.dependencies { implementation(libs.compose.multiplatform.ui.test) }

        // Tests that use core:testing, which has no wasmJs target; KmpFeatureConventionPlugin wires it here.
        matching { it.name == "jvmTest" || it.name == "iosArm64Test" || it.name == "iosSimulatorArm64Test" }
            .configureEach { dependsOn(getByName("nonWebTest")) }

        jvmTest.dependencies { implementation(compose.desktop.currentOs) }
    }
}
