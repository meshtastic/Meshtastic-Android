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
    // Deliberately a withCompilations predicate, NOT withAndroidTarget()/withApple(): this repo
    // applies `com.android.kotlin.multiplatform.library` (see KmpLibraryConventionPlugin.kt), and
    // withAndroidTarget() silently fails to attach androidMain to a custom group under that specific
    // plugin — a confirmed, open JetBrains bug (KT-80409); androidMain stays wired directly to
    // commonMain instead, which broke this module's Android compile the one time this code tried
    // withAndroidTarget(). A KGP engineer's own documented workaround for that bug is to drop to the
    // lower-level withCompilations predicate instead of the named helper, which is what this does.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyHierarchyTemplate(KotlinHierarchyTemplate.default) {
        common { group("nonWeb") { withCompilations { it.target.targetName != "wasmJs" } } }
    }

    // The predicate above only reaches the two *leaf* iOS compilations (iosArm64Main,
    // iosSimulatorArm64Main) — each gains nonWebMain as an extra, direct dependsOn edge alongside
    // their existing one on `iosMain`, but the pre-existing shared `iosMain` intermediate itself
    // (created by the default hierarchy template once iosArm64()/iosSimulatorArm64() are
    // registered, and where NoopStubs.kt's `actual`s live) never gains an edge to nonWebMain. Kotlin
    // checks each source set's *own* expect/actual visibility against its own dependsOn closure, not
    // the leaf's merged one, so iosMain's actuals can't see nonWebMain's expects even though both are
    // (separately) ancestors of the same two leaves — confirmed via a printSourceSetHierarchy dump.
    // Wire it explicitly, the same "drop to a lower-level, explicit construct" pattern KT-80409's own
    // workaround uses for the analogous Android problem.
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

        androidMain.dependencies {
            implementation(libs.androidx.lifecycle.process)
            implementation(libs.jetbrains.lifecycle.runtime)
        }

        commonTest.dependencies { implementation(projects.core.testing) }
    }
}
