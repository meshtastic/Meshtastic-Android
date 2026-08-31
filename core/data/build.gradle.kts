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
    alias(libs.plugins.meshtastic.kotlinx.serialization)
    alias(libs.plugins.meshtastic.koin)
}

kotlin {
    android { withHostTest { isIncludeAndroidResources = true } }

    // wasmJs { browser() } required repo-wide by KGP's root npm resolver — see core:prefs/build.gradle.kts's
    // comment for the full story (webApp's binaries.executable(), the wasmJsBrowserTest/karma gap it
    // exposed, and how that's now handled centrally).
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    // nonWebMain: core:takserver (ATAK, out of this effort's v0 scope) and the Room/sqlite-bundled runtime
    // (jvmAndroidMain) have no wasmJs story. jvmAndroidMain nests inside it, replacing the
    // meshtastic.kmp.jvm.android convention plugin -- which would need a second, conflicting
    // applyHierarchyTemplate call (Gradle only allows one per project) -- so its dependency block keeps
    // resolving unchanged. Predicate, not withAndroidTarget()/withApple(): those silently drop androidMain
    // under com.android.kotlin.multiplatform.library (KT-80409), same as core:ble/core:network.
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
            api(projects.core.repository)
            implementation(projects.core.common)
            implementation(projects.core.database)
            implementation(projects.core.datastore)
            implementation(projects.core.di)
            implementation(projects.core.model)
            implementation(projects.core.network)
            implementation(projects.core.prefs)
            implementation(libs.meshtastic.protobufs)

            implementation(libs.jetbrains.lifecycle.runtime)
            implementation(libs.androidx.paging.common)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.serialization.json.okio)
            implementation(libs.okio)
            implementation(libs.kermit)
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.kotlinx.collections.immutable)
        }

        // android/jvm/ios only: ATAK/TAK-server support is a niche feature out of this effort's v0 scope
        // (see the workpad's [SCOPING] entry) -- core:takserver itself has no wasmJs target and needs none;
        // nothing in this module's own commonMain source actually uses its API (confirmed via grep), so this
        // dependency line moves wholesale rather than being duplicated.
        getByName("nonWebMain").dependencies { implementation(projects.core.takserver) }

        // Room / SQLite runtime shared between Android and Desktop JVM targets
        getByName("jvmAndroidMain") {
            dependencies {
                implementation(libs.androidx.room.runtime)
                implementation(libs.androidx.room.paging)
                implementation(libs.androidx.sqlite.bundled)
            }
        }

        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.core.location.altitude)
        }

        // core:database exposes Room only as `implementation` (its own commonMain
        // `androidx.room.paging` dependency), so `RoomDatabase` -- the supertype of the
        // `MeshtasticDatabase` this module's repositories reference via `DatabaseProvider` -- isn't
        // visible to external consumers by default; jvmAndroidMain above already redeclares it
        // directly for the same reason. wasmJs needs the identical redeclaration (sqlite-bundled
        // excluded: native-only, unrelated to RoomDatabase itself).
        wasmJsMain.dependencies {
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.room.paging)
        }

        // android/jvm/ios only: core:testing has no wasmJs target of its own (same gap
        // core:ble/core:database/core:network hit) -- confirmed via a real
        // :core:data:compileTestKotlinWasmJs run that this dependency does block that task (it fails at
        // configuration, "no matching variant of project :core:testing"), so the 14 commonTest files that
        // actually import org.meshtastic.core.testing moved to nonWebTest with it; the other 19 (confirmed
        // via grep) don't reference it and stayed in commonTest.
        getByName("nonWebTest").dependencies { implementation(projects.core.testing) }

        getByName("androidHostTest") { dependencies { runtimeOnly(libs.androidx.sqlite.bundled.jvm) } }
    }
}
