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
    alias(libs.plugins.meshtastic.android.room)
    alias(libs.plugins.meshtastic.kotlinx.serialization)
    alias(libs.plugins.kotlin.parcelize)
    id("meshtastic.koin")
}

kotlin {
    android {
        namespace = "org.meshtastic.core.database"
        withHostTest { isIncludeAndroidResources = true }
        withDeviceTest { instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.androidx.sqlite.bundled)

            api(projects.core.common)
            implementation(projects.core.di)
            api(projects.core.model)
            implementation(projects.core.proto)
            implementation(projects.core.resources)
            implementation(libs.androidx.room.paging)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kermit)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.androidx.room.testing)
        }
        androidMain.dependencies { implementation(libs.javax.inject) }

        val androidHostTest by getting {
            dependencies {
                implementation(libs.androidx.room.testing)
                implementation(libs.androidx.test.core)
                implementation(libs.androidx.test.ext.junit)
                implementation(libs.junit)
                implementation(libs.robolectric)
            }
        }
        val androidDeviceTest by getting {
            dependencies {
                implementation(libs.androidx.room.testing)
                implementation(libs.androidx.test.ext.junit)
                implementation(libs.androidx.test.runner)
            }
        }
    }
}

dependencies {
    "kspAndroidHostTest"(libs.androidx.room.compiler)
    "kspAndroidDeviceTest"(libs.androidx.room.compiler)
}
