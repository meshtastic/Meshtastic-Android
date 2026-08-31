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

plugins { alias(libs.plugins.meshtastic.kmp.feature) }

kotlin {
    // wasmJs { browser() } required repo-wide by KGP's root npm resolver — see core:prefs/build.gradle.kts's comment.
    // No custom
    // hierarchy group is needed for MAIN — zero expect/actual declarations and zero java.*/android.* imports
    // in commonMain (confirmed via grep), and a fresh sweep of every wasmJs-relevant dependency's own
    // nonWebMain (core:database/core:prefs/core:network/core:datastore/core:ble) found zero references to
    // any of their nonWebMain-only types from this module's commonMain — same shape as core:domain, unlike
    // feature:connections (which needed an interface-extraction seam for two core:datastore types).
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser() }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.data)
            implementation(projects.core.database)
            implementation(projects.core.domain)
            implementation(projects.core.model)
            implementation(projects.core.navigation)
            implementation(projects.core.prefs)
            implementation(libs.meshtastic.protobufs)
            implementation(projects.core.resources)
            implementation(projects.core.service)
            implementation(projects.core.ui)

            implementation(libs.androidx.paging.common)
            implementation(libs.androidx.paging.compose)
            implementation(libs.kotlinx.collections.immutable)

            // JetBrains Material 3 Adaptive (multiplatform ListDetailPaneScaffold)
            implementation(libs.jetbrains.compose.material3.adaptive)
            implementation(libs.jetbrains.compose.material3.adaptive.layout)
            implementation(libs.jetbrains.compose.material3.adaptive.navigation)
            implementation(libs.jetbrains.compose.material3.adaptive.navigation3)
        }

        androidMain.dependencies { implementation(libs.androidx.work.runtime.ktx) }

        commonTest.dependencies { implementation(libs.compose.multiplatform.ui.test) }

        // TEST only: core:testing has no wasmJs target (same gap every other module this session hit).
        // 3 of 14 commonTest files import it (confirmed via grep, not assumed): MessageViewModelTest.kt,
        // MessageViewModelTranslationTest.kt, ContactsViewModelTest.kt — moved to a manually-created
        // nonWebTest source set (this module uses no hierarchy template for MAIN, so nothing auto-creates
        // one, same as core:domain); the other 11 stay in commonTest and compile for wasmJs. core:testing
        // itself is wired into nonWebTest by KmpFeatureConventionPlugin (afterEvaluate, routes to
        // nonWebTest when present) — not added here.
        val nonWebTest by creating { dependsOn(commonTest.get()) }

        // No `android { withHostTest {} }` call in this module, so androidHostTest never materializes —
        // matching{}.configureEach{} degrades to a harmless no-op for whichever of these leaf test source
        // sets isn't registered, instead of throwing the way a bare getByName("androidHostTest") would at
        // configuration time (same fix core:ui's pass required for the same reason).
        matching {
            it.name == "jvmTest" ||
                it.name == "androidHostTest" ||
                it.name == "iosArm64Test" ||
                it.name == "iosSimulatorArm64Test"
        }
            .configureEach { dependsOn(nonWebTest) }

        jvmTest.dependencies { implementation(compose.desktop.currentOs) }
    }
}

// Gradle's KMP variant resolution follows `available-at` redirects in module
