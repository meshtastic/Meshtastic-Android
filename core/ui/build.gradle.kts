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
    alias(libs.plugins.meshtastic.kmp.library.compose)
    alias(libs.plugins.meshtastic.kotlinx.serialization)
    alias(libs.plugins.meshtastic.kmp.jvm.android)
    alias(libs.plugins.meshtastic.koin)
}

kotlin {
    // Required for CMP files/ resources (emoji-data.json) to be packaged as Android assets.
    // Without this, Res.readBytes() throws MissingResourceException at runtime.
    android { androidResources.enable = true }

    // Library module: bare wasmJs(), no browser() (that's for the eventual webApp executable). No custom
    // hierarchy group is needed for MAIN, same shape as core:common/core:model/core:repository/core:service:
    // every one of this module's own dependencies (core/*, coil, jetbrains-markdown, qrcode-kotlin, every
    // compose-multiplatform-*/jetbrains-* artifact) is confirmed to publish a wasmJs variant, and the only
    // three commonMain files with `expect`/`actual` (PlatformUtils.kt, ClipboardUtils.kt, HtmlUtils.kt) plus
    // three more found while verifying this pass (ScreenUtils.kt, DynamicColorScheme.kt,
    // DropDownPreference.kt's enumEntriesOf/isDeprecatedEnumEntry) all gained real wasmJsMain actuals below —
    // none of them needed a native-only dependency hidden from wasmJs, so no `applyHierarchyTemplate` call is
    // needed here (calling it a second time, after `meshtastic.kmp.jvm.android`'s own, would fail — Gradle
    // only allows one per project — which is why core:common/core:model keep the plugin and skip the call too).
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs()

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.data)
            implementation(projects.core.database)
            implementation(projects.core.datastore)
            implementation(projects.core.model)
            implementation(projects.core.navigation)
            implementation(projects.core.prefs)
            implementation(libs.meshtastic.protobufs)
            implementation(projects.core.repository)
            implementation(projects.core.resources)
            implementation(projects.core.service)

            implementation(libs.compose.multiplatform.animation)
            implementation(libs.compose.multiplatform.material3)
            implementation(libs.compose.multiplatform.ui)
            implementation(libs.compose.multiplatform.foundation)
            api(libs.compose.multiplatform.ui.tooling.preview)

            implementation(libs.coil)
            implementation(libs.jetbrains.markdown)
            implementation(libs.kermit)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.qrcode.kotlin)
            implementation(libs.jetbrains.compose.material3.adaptive)
            implementation(libs.jetbrains.compose.material3.adaptive.layout)
            implementation(libs.jetbrains.compose.material3.adaptive.navigation)
            implementation(libs.jetbrains.compose.material3.adaptive.navigation.suite)
            api(libs.jetbrains.navigation3.ui)
            // navigation3-ui's own POM marks this runtime-scope, so it never reaches any
            // compile classpath transitively — declare it directly. Consumers (e.g.
            // feature:docs) import androidx.navigationevent.compose.* directly.
            api(libs.jetbrains.navigationevent.compose)
            implementation(libs.jetbrains.compose.material3.adaptive.navigation3)
            implementation(libs.jetbrains.lifecycle.viewmodel.navigation3)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
        }

        getByName("jvmAndroidMain") { dependencies { implementation(libs.compose.multiplatform.ui.tooling) } }

        androidMain.dependencies { implementation(libs.androidx.activity.compose) }

        // `rememberOpenUrl`'s wasmJs actual (`window.open`) is the only wasmJsMain file needing a JS-facing
        // dependency; everything else in wasmJsMain is either a pure Kotlin no-op or (createClipEntry,
        // KeepScreenOn) uses only Compose Multiplatform's own API / a hand-declared `external` (matching
        // core:ble's `WebBluetoothApi.kt` idiom for the Screen Wake Lock API kotlinx-browser doesn't bind).
        wasmJsMain.dependencies { implementation(libs.kotlinx.browser) }

        commonTest.dependencies { implementation(libs.compose.multiplatform.ui.test) }

        // TEST only: core:testing has no wasmJs target (same gap core:ble/core:database/core:network/
        // core:data/core:service all hit) — confirmed via grep that exactly 3 of this module's 27 commonTest
        // files import org.meshtastic.core.testing (ConnectionsViewModelTest.kt, ProtoExtensionsTest.kt,
        // SharedContactViewModelTest.kt); those 3 move to nonWebTest, the other 24 (including every
        // runComposeUiTest-based UI test — compose-multiplatform-ui-test itself does publish a wasmJs variant)
        // stay in commonTest. `libs.junit` also moves here: grep found zero `org.junit.*` usage anywhere in
        // this module's tests (vestigial, presumably pre-dating the migration to `kotlin.test`), and it is a
        // plain non-KMP jar with no wasmJs metadata at all, so leaving it in commonTest risked being a second,
        // indistinguishable cause of any wasmJs test-resolution failure.
        //
        // No applyHierarchyTemplate call happens in this script (see the MAIN comment above), so the default
        // template's shared intermediate test source sets are not synchronously available by name this early
        // (confirmed: core:ui has no `android { withHostTest {} }` call at all, so `androidHostTest` doesn't
        // even exist — unlike core:service/core:data, which all call `withHostTest {}` and could `getByName`
        // it directly). `matching{}.configureEach{}` degrades to a harmless no-op for whichever of these leaf
        // test source sets isn't registered, instead of throwing.
        val nonWebTest by creating {
            dependsOn(commonTest.get())
            dependencies {
                implementation(projects.core.testing)
                implementation(libs.junit)
            }
        }
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
