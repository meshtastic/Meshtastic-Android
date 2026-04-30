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
    id("meshtastic.koin")
}

kotlin {
    android {
        namespace = "org.meshtastic.core.service"
        androidResources.enable = false
        withHostTest { isIncludeAndroidResources = true }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.core.repository)
            implementation(projects.core.common)
            implementation(projects.core.data)
            implementation(projects.core.database)
            implementation(projects.core.di)
            implementation(projects.core.model)
            implementation(projects.core.navigation)
            implementation(projects.core.network)
            implementation(projects.core.ble)
            implementation(projects.core.prefs)
            implementation(projects.core.proto)
            implementation(projects.core.takserver)

            implementation(libs.jetbrains.lifecycle.runtime)
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kermit)
        }

        androidMain.dependencies {
            api(projects.core.api)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.work.runtime.ktx)
            implementation(libs.koin.android)
            implementation(libs.koin.androidx.workmanager)
        }

        val androidHostTest by getting {
            dependencies {
                implementation(projects.core.testing)
                implementation(libs.androidx.test.ext.junit)
                implementation(libs.androidx.work.testing)
            }
        }

        commonTest.dependencies { implementation(libs.kotlinx.coroutines.test) }
    }
}
