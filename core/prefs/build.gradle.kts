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

    // Library module: bare wasmJs(), no browser() (that's for the eventual webApp executable).
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs()

    // nonWebMain: androidx.datastore.preferences has no wasmJs variant (the `Preferences` type itself doesn't
    // resolve there), so DataStorePrefsStore — the adapter wrapping a real DataStore<Preferences> — and the
    // Android-only CorePrefsAndroidModule live here/in androidMain instead of commonMain. All 17 `*PrefsImpl`
    // classes themselves stay in commonMain: they depend only on the platform-neutral PrefsStore/PrefsSnapshot/
    // PrefsKey abstraction (see core/prefs/store/PrefsStore.kt), not on Preferences directly, so unlike
    // core:database's DatabaseManager split, nothing about their own code needs to move. Predicate, not
    // withAndroidTarget()/withApple() — those silently drop androidMain under
    // com.android.kotlin.multiplatform.library (KT-80409). See core/ble/build.gradle.kts for the same pattern.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyHierarchyTemplate(KotlinHierarchyTemplate.default) {
        common { group("nonWeb") { withCompilations { it.target.targetName != "wasmJs" } } }
    }

    // The predicate above misses iosMain itself (only reaches the two leaf iOS compilations), so any
    // nonWebMain-only actual/declaration can't see nonWebMain's members without this explicit edge.
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

        // android/jvm/ios only (see hierarchy template above) — androidx.datastore.preferences has no wasmJs
        // variant, and DataStorePrefsStore (the sole consumer) lives here.
        getByName("nonWebMain").dependencies { implementation(libs.androidx.datastore.preferences) }

        wasmJsMain.dependencies { implementation(libs.kotlinx.browser) }

        // All 7 existing commonTest files construct a real DataStore<Preferences> via PreferenceDataStoreFactory,
        // so they move to nonWebTest wholesale (same nonWebTest split core:database's DataStore/DAO tests got) —
        // nothing wasmJs-reachable in this module's tests needs a real DataStore today. No extra dependency
        // needed here: kotlin("test")/kotest/turbine/coroutines-test come from configureKmpTestDependencies()'s
        // commonTest additions, and nonWebTest inherits them via the hierarchy template's dependsOn edge.
    }
}
