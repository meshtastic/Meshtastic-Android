/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
    alias(libs.plugins.meshtastic.koin)
}

kotlin {
    jvm()

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

            implementation(libs.compose.multiplatform.material3)
            implementation(libs.compose.multiplatform.materialIconsExtended)
            implementation(libs.compose.multiplatform.ui)
            implementation(libs.compose.multiplatform.foundation)
            implementation(libs.compose.multiplatform.ui.tooling)

            implementation(libs.kermit)
            implementation(libs.koin.compose.viewmodel)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.zxing.core)
        }

        commonTest.dependencies {
            implementation(libs.junit)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.turbine)
        }

        androidUnitTest.dependencies {
            implementation(libs.mockk)
            implementation(libs.androidx.test.runner)
        }
    }
}
