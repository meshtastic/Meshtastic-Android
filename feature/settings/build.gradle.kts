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
    // meshtastic.kmp.jvm.android NOT applied: its own applyHierarchyTemplate() call for the jvmAndroidMain group
    // would conflict with this module's own call below (needed for the nonWeb group) -- Gradle allows exactly one
    // per project. The jvmAndroid group it used to provide is nested inside nonWeb instead, matching
    // core:network's identical precedent. AboutLibrariesLoader.kt's existing jvmAndroidMain source set (and its
    // one file) is unaffected -- same source set, just created a different way.
}

kotlin {
    // Library module: bare wasmJs(), no browser() — see core:prefs/build.gradle.kts's comment for why the
    // repo-wide browser() experiment was reverted.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs()

    // nonWebMain: TAKConfigItemList.kt/TAKConfigPreviews.kt (module config + local TAK server screens) and the
    // tak/TakPermissionUtil.kt + tak/PrefExporter.kt expects they alone use depend on core:takserver directly or
    // exist only to serve it. core:takserver can never get a wasmJs target -- its production implementation binds
    // an inbound TLS SSLServerSocket listener, which a browser sandbox can never accept (same reasoning as
    // core:service's TakServerIntegration seam). jvmAndroid nests inside nonWeb to replace the
    // meshtastic.kmp.jvm.android convention plugin (see the comment on the plugins block above). Predicate, not
    // withAndroidTarget()/withApple() -- those silently drop androidMain under
    // com.android.kotlin.multiplatform.library (KT-80409), same as core:ble/core:network.
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

    // The predicate above misses iosMain itself (only reaches the two leaf iOS compilations), same gap
    // core:ble/core:network hit.
    sourceSets.getByName("iosMain") { dependsOn(sourceSets.getByName("nonWebMain")) }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.data)
            implementation(projects.core.database)
            implementation(projects.core.datastore)
            implementation(projects.core.domain)
            implementation(projects.core.model)
            implementation(projects.core.navigation)
            implementation(projects.core.network)
            implementation(libs.meshtastic.protobufs)
            implementation(projects.core.repository)
            implementation(projects.core.service)
            implementation(projects.core.resources)
            implementation(projects.core.ui)
            implementation(projects.core.di)

            implementation(libs.kotlinx.atomicfu)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.aboutlibraries.compose.m3)
            implementation(libs.coil)
        }

        // android/jvm/ios only (see hierarchy template above): TAKConfigItemList.kt/TAKConfigPreviews.kt use
        // core:takserver directly, and nothing else in commonMain references it (confirmed via grep).
        getByName("nonWebMain").dependencies { implementation(projects.core.takserver) }

        androidMain.dependencies {
            implementation(projects.core.barcode)
            implementation(projects.core.nfc)
            implementation(libs.androidx.appcompat)
        }

        commonTest.dependencies {
            implementation(projects.core.datastore)
            implementation(libs.compose.multiplatform.ui.test)
        }

        // android/jvm/ios only: core:testing has no wasmJs target (same gap every other module this session hit).
        // core:testing itself is wired into nonWebTest by KmpFeatureConventionPlugin's afterEvaluate -- not
        // declared here, per the convention feature:connections/feature:messaging established.
        getByName("nonWebTest") { dependsOn(commonTest.get()) }

        // Degrade-safe: this module has no `android { withHostTest {} }` call, so androidHostTest never
        // materializes -- core:ui's precedent, not core:domain's bare getByName.
        matching {
            it.name == "jvmTest" ||
                it.name == "androidHostTest" ||
                it.name == "iosArm64Test" ||
                it.name == "iosSimulatorArm64Test"
        }
            .configureEach { dependsOn(getByName("nonWebTest")) }

        jvmTest.dependencies { implementation(compose.desktop.currentOs) }
    }
}
