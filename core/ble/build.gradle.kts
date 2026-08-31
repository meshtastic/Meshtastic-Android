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
    android { withHostTest { isIncludeAndroidResources = true } }

    // wasmJs { browser() } required repo-wide by KGP's root npm resolver — see core:prefs/build.gradle.kts's
    // comment for the full story (webApp's binaries.executable(), the wasmJsBrowserTest/karma gap it
    // exposed, and how that's now handled centrally).
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    // nonWebMain: Kable has no wasmJs target, so its dependents live here instead of commonMain.
    // Predicate, not withAndroidTarget()/withApple() — those silently drop androidMain under
    // com.android.kotlin.multiplatform.library (KT-80409).
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyHierarchyTemplate(KotlinHierarchyTemplate.default) {
        common { group("nonWeb") { withCompilations { it.target.targetName != "wasmJs" } } }
    }

    // The predicate above misses iosMain itself (only reaches the two leaf iOS compilations), so
    // NoopStubs.kt's actuals can't see nonWebMain's expects without this explicit edge.
    sourceSets.getByName("iosMain") { dependsOn(sourceSets.getByName("nonWebMain")) }

    sourceSets {
        commonMain.dependencies {
            // api: BleScanStartException implements core.common's ExpectedCondition in its public supertype list.
            api(projects.core.common)
            implementation(projects.core.di)
            implementation(projects.core.model)

            implementation(libs.kermit)
            implementation(libs.kotlinx.coroutines.core)
        }

        // android/jvm/ios only (see hierarchy template above) — Kable has no wasmJs target.
        getByName("nonWebMain").dependencies { implementation(libs.kable.core) }

        wasmJsMain.dependencies { implementation(libs.kotlinx.browser) }

        androidMain.dependencies {
            implementation(libs.androidx.lifecycle.process)
            implementation(libs.jetbrains.lifecycle.runtime)
        }

        // kotlin("test")/kotlinx-coroutines-test only — previously came transitively through core:testing's own
        // api() exposure, which commonTest can no longer depend on directly (see nonWebTest below).
        // BleRetryTest/DisconnectReasonTest still need kotlin("test"); BleRetryTest also needs coroutines-test.
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }

        // android/jvm/ios only (see hierarchy template above): core:testing has no wasmJs target, and every
        // commonTest file that references Kable directly (BleExceptionClassifierTest, KableBleConnectionTest,
        // KableStateMappingTest, KableMeshtasticRadioProfile{,Exception}Test, MeshtasticBleDeviceRssiTest,
        // BleScanStartExceptionTest — 7 of 9, confirmed via grep for `com.juul.kable`/`core.testing` imports, not
        // assumed) moved here too, since commonTest can't see nonWebMain's Kable dependency either. Only
        // BleRetryTest/DisconnectReasonTest stay in commonTest. This was a latent gap: core:ble's own
        // compileTestKotlinWasmJs was never actually exercised until the webApp milestone pass tried building
        // it (a standing `[DEFERRED]` since the core:ble milestone, never revisited until now). Kept regardless
        // of that pass's own `browser()` experiment being reverted (see core:prefs/build.gradle.kts's comment) —
        // it's a real, independent fix: compileTestKotlinWasmJs for this module now passes on its own merits.
        getByName("nonWebTest").dependencies { implementation(projects.core.testing) }
    }
}
