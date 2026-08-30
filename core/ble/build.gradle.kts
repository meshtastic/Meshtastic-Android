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

// `wasmJs { browser() }`'s auto-applied WasmNpmResolverPlugin reaches into the root project's
// Project.plugins, which org.gradle.isolated-projects=true (gradle.properties, repo-wide) forbids
// from a subproject — that broke configuration for every task in the whole repo, not just wasmJs
// ones, since Gradle configures this module's build script whenever anything depends on it. Two
// fixes: (1) core:ble is a library, not an executable — it needs the wasmJs *compile* target only,
// not browser()'s npm/webpack tooling, which is IP-incompatible today (JetBrains: JS/Wasm + Isolated
// Projects is not yet supported). (2) gate the target registration behind a property so it is absent
// from the configuration graph by default — pass -Pmeshtastic.web=true to build it locally; it must
// stay off for the standard baseline/CI until upstream JS/Wasm + IP support lands.
val webEnabled = providers.gradleProperty("meshtastic.web").isPresent

kotlin {
    android { withHostTest { isIncludeAndroidResources = true } }

    if (webEnabled) {
        @OptIn(ExperimentalWasmDsl::class)
        wasmJs()
    }

    // Kable (this module's BLE library on android/jvm/ios) has no wasmJs target. Everything that
    // depends on it lives in the `nonWebMain` intermediate source set this creates instead of
    // `commonMain`, so wasmJs can join the hierarchy without an actual for Kable-typed expects.
    // Named `nonWeb`, not `native`: `KotlinHierarchyTemplate.default` already defines a built-in
    // `native` group (-> `nativeMain`) for Kotlin/Native targets (iOS), which this predicate would
    // otherwise silently collide with. Kept outside the `webEnabled` guard above — with no wasmJs
    // target present this groups every compilation and is an inert no-op layer.
    //
    // withJvm()/withAndroidTarget()/withApple() (NOT a withCompilations predicate excluding wasmJs):
    // the predicate form grafts `nonWeb` onto leaf compilations only, bypassing the default
    // template's own `iosMain` intermediate — iOS's actuals (NoopStubs.kt) then have no dependsOn
    // path to nonWebMain's expects (KablePlatformSetup.kt) even though both are ancestors of the
    // same leaf targets, because Kotlin's expect/actual visibility check walks each source set's own
    // dependsOn closure, not the leaf's merged one. Nesting the default template's own named groups
    // under this one keeps `iosMain` (and its existing dependsOn edges) intact underneath it.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyHierarchyTemplate(KotlinHierarchyTemplate.default) {
        common {
            group("nonWeb") {
                withJvm()
                withAndroidTarget()
                withApple()
            }
        }
    }

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

        androidMain.dependencies {
            implementation(libs.androidx.lifecycle.process)
            implementation(libs.jetbrains.lifecycle.runtime)
        }

        commonTest.dependencies { implementation(projects.core.testing) }
    }
}
