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
    alias(libs.plugins.meshtastic.koin)
}

kotlin {
    android { withHostTest {} }

    // Every module reachable from webApp's executable needs browser(), not a bare wasmJs(), or KGP's root npm resolver
    // fails. Its wasmJsBrowserTest is disabled centrally in KotlinAndroid.kt.
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    // nonWebMain: androidx.datastore.preferences has no wasmJs variant, so DataStorePrefsStore lives there and every
    // *PrefsImpl uses the PrefsStore abstraction. A compilation predicate, because withAndroidTarget()/withApple() drop
    // androidMain under com.android.kotlin.multiplatform.library (KT-80409).
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyHierarchyTemplate(KotlinHierarchyTemplate.default) {
        common { group("nonWeb") { withCompilations { it.target.targetName != "wasmJs" } } }
    }

    // The predicate reaches only the iOS leaf compilations, not iosMain itself.
    sourceSets.getByName("iosMain") { dependsOn(sourceSets.getByName("nonWebMain")) }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.repository)
            implementation(projects.core.common)
            implementation(projects.core.di)

            implementation(libs.kotlinx.atomicfu)
            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.kotlinx.coroutines.core)
        }

        getByName("nonWebMain").dependencies { implementation(libs.androidx.datastore.preferences) }

        wasmJsMain.dependencies { implementation(libs.kotlinx.browser) }
    }
}
