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

plugins { alias(libs.plugins.meshtastic.kmp.feature) }

kotlin {
    android { withHostTest { isIncludeAndroidResources = true } }

    // wasmJs { browser() } required repo-wide by KGP's root npm resolver — see core:prefs/build.gradle.kts's
    // comment for the full story (webApp's binaries.executable(), the wasmJsBrowserTest/karma gap it
    // exposed, and how that's now handled centrally).
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    // nonWebMain: ScannerViewModel/CommonGetDiscoveredDevicesUseCase depend on RecentAddressesSource/
    // PendingFirmwareRecoverySource (feature-local interfaces, commonMain) — but the real, Preferences-backed
    // adapters (DataSourceAdapters.kt) delegate to core:datastore's RecentAddressesDataSource/
    // FirmwareRecoveryDataSource, which have no wasmJs target (androidx.datastore.preferences publishes none
    // — see core:datastore's own wasmJs milestone). Predicate, not withAndroidTarget()/withApple() — those
    // silently drop androidMain under com.android.kotlin.multiplatform.library (KT-80409), same as core:ble.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyHierarchyTemplate(KotlinHierarchyTemplate.default) {
        common { group("nonWeb") { withCompilations { it.target.targetName != "wasmJs" } } }
    }

    // The predicate above misses iosMain itself (only reaches the two leaf iOS compilations), same gap core:ble hit.
    sourceSets.getByName("iosMain") { dependsOn(sourceSets.getByName("nonWebMain")) }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.data)
            implementation(projects.core.database)
            implementation(projects.core.datastore)
            implementation(projects.core.di)
            implementation(projects.core.domain)
            implementation(projects.core.model)
            implementation(projects.core.navigation)
            implementation(projects.core.prefs)
            implementation(libs.meshtastic.protobufs)
            implementation(projects.core.resources)
            implementation(projects.core.service)
            implementation(projects.core.ui)
            implementation(projects.core.ble)
            implementation(projects.core.network)
        }

        androidMain.dependencies { implementation(libs.usb.serial.android) }

        // Compose UI tests live in jvmTest, not commonTest: this module enables android host tests, and the
        // androidHostTest stubs leave Build.FINGERPRINT null, which the Compose Robolectric idling strategy NPEs on.
        jvmTest.dependencies {
            implementation(libs.compose.multiplatform.ui.test)
            implementation(compose.desktop.currentOs)
        }

        // TEST only: 4 of 6 commonTest files depend on core:testing (no wasmJs target — same gap every
        // other module this session hit), confirmed via grep for the import, not assumed:
        // ScannerViewModelHarness.kt/ScannerViewModelTest.kt/TcpDiscoveryHelpersTest.kt/
        // CommonGetDiscoveredDevicesUseCaseTest.kt moved to the nonWebTest source set the hierarchy
        // template above already creates (android/jvm/iOS only); the other 2 stay in commonTest and
        // compile for wasmJs. core:testing itself is wired into nonWebTest by KmpFeatureConventionPlugin
        // (afterEvaluate, routes to nonWebTest when present) — not added here.
        getByName("nonWebTest") { dependsOn(commonTest.get()) }
    }
}
