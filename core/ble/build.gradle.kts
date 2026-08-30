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

meshtasticKmpTargets {
    web.set(true)
    // Kable (core/ble's BLE library on android/jvm/ios) has no wasmJs target. Everything that
    // depends on it lives in the `nativeMain` intermediate source set this creates instead of
    // `commonMain`, so wasmJs can join the hierarchy without an actual for Kable-typed expects.
    hoistNativeOnlyDependencies.set(true)
}

kotlin {
    android { withHostTest { isIncludeAndroidResources = true } }

    sourceSets {
        commonMain.dependencies {
            // api: BleScanStartException implements core.common's ExpectedCondition in its public supertype list.
            api(projects.core.common)
            implementation(projects.core.di)
            implementation(projects.core.model)

            implementation(libs.kermit)
            implementation(libs.kotlinx.coroutines.core)
        }

        // android/jvm/ios only (see meshtasticKmpTargets above) — Kable has no wasmJs target.
        getByName("nativeMain").dependencies { implementation(libs.kable.core) }

        androidMain.dependencies {
            implementation(libs.androidx.lifecycle.process)
            implementation(libs.jetbrains.lifecycle.runtime)
        }

        commonTest.dependencies { implementation(projects.core.testing) }
    }
}
