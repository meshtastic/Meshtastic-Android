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
    alias(libs.plugins.meshtastic.kmp.feature)
    alias(libs.plugins.meshtastic.kmp.web)
    alias(libs.plugins.meshtastic.kotlinx.serialization)
}

kotlin {
    android { withHostTest { isIncludeAndroidResources = true } }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.coil)
            implementation(projects.core.common)
            implementation(projects.core.domain)
            implementation(projects.core.model)
            implementation(projects.core.navigation)
            implementation(libs.meshtastic.protobufs)
            implementation(projects.core.repository)
            implementation(projects.core.resources)
            implementation(projects.core.ui)
            implementation(projects.core.di)

            implementation(libs.kotlinx.serialization.json)
            implementation(libs.markdown.renderer)
            implementation(libs.markdown.renderer.m3)
            implementation(libs.vico.compose)
            implementation(libs.vico.compose.m3)

            // JetBrains Material 3 Adaptive (multiplatform ListDetailPaneScaffold)
            implementation(libs.jetbrains.compose.material3.adaptive)
            implementation(libs.jetbrains.compose.material3.adaptive.layout)
            implementation(libs.jetbrains.compose.material3.adaptive.navigation)
            implementation(libs.jetbrains.compose.material3.adaptive.navigation3)
        }

        // sceneview-compose publishes no wasmJs variant.
        getByName("nonWebMain").dependencies {
            // The desktop renderer's POM drags a mac-only Compose runtime onto every OS's classpath. The
            // string form is the one overload that takes an exclude; the version still comes from the catalog.
            implementation("io.github.sceneview:sceneview-compose:${libs.versions.sceneview.get()}") {
                exclude(group = "org.jetbrains.compose.desktop", module = "desktop-jvm-macos-arm64")
                // Fuel 2.3.1 (the Android renderer's URL loader, unused here) ships the pre-Parcelize runtime.
                exclude(group = "org.jetbrains.kotlin", module = "kotlin-android-extensions-runtime")
            }
        }

        androidMain.dependencies {
            implementation(libs.markdown.renderer.android)
            // The Android renderer under sceneview-compose, which only ships it at runtime scope.
            implementation("io.github.sceneview:sceneview:${libs.versions.sceneview.get()}") {
                exclude(group = "org.jetbrains.kotlin", module = "kotlin-android-extensions-runtime")
            }
        }

        // PacketRepository returns PagingData, which core:repository takes as implementation. A mock of it needs the
        // type on the test classpath, and on wasmJs nothing else puts it there.
        commonTest.dependencies { implementation(libs.androidx.paging.common) }

        // Compose UI tests live in jvmTest, not commonTest: this module enables android host tests, and the
        // androidHostTest stubs leave Build.FINGERPRINT null, which the Compose Robolectric idling strategy NPEs on.
        jvmTest.dependencies {
            implementation(libs.compose.multiplatform.ui.test)
            implementation(compose.desktop.currentOs)
        }
    }
}

// The test bundle never reports to Karma: headless Chrome stays silent until the 300 s no-activity timeout, with no
// test started. Its wasmJs tests compile, but nothing runs them until that is diagnosed.
meshtasticWeb.browserTests = false
