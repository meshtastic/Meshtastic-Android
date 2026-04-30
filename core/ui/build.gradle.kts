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
    id("meshtastic.kmp.jvm.android")
    id("meshtastic.koin")
}

kotlin {
    android {
        namespace = "org.meshtastic.core.ui"
        androidResources.enable = false
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.data)
            implementation(projects.core.database)
            implementation(projects.core.datastore)
            implementation(projects.core.model)
            implementation(projects.core.navigation)
            implementation(projects.core.prefs)
            implementation(projects.core.proto)
            implementation(projects.core.repository)
            implementation(projects.core.resources)
            implementation(projects.core.service)

            implementation(libs.compose.multiplatform.animation)
            implementation(libs.compose.multiplatform.material3)
            implementation(libs.compose.multiplatform.ui)
            implementation(libs.compose.multiplatform.foundation)
            api(libs.compose.multiplatform.ui.tooling.preview)

            implementation(libs.kermit)
            implementation(libs.koin.compose.viewmodel)
            implementation(libs.qrcode.kotlin)
            implementation(libs.jetbrains.compose.material3.adaptive)
            implementation(libs.jetbrains.compose.material3.adaptive.layout)
            implementation(libs.jetbrains.compose.material3.adaptive.navigation)
            implementation(libs.jetbrains.compose.material3.adaptive.navigation.suite)
            implementation(libs.jetbrains.navigation3.ui)
            implementation(libs.jetbrains.compose.material3.adaptive.navigation3)
            implementation(libs.jetbrains.lifecycle.viewmodel.navigation3)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
        }

        val jvmAndroidMain by getting { dependencies { implementation(libs.compose.multiplatform.ui.tooling) } }

        androidMain.dependencies { implementation(libs.androidx.activity.compose) }

        commonTest.dependencies {
            implementation(projects.core.testing)
            implementation(libs.junit)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.compose.multiplatform.ui.test)
        }

        jvmTest.dependencies { implementation(compose.desktop.currentOs) }
    }
}
