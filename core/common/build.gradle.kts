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
    alias(libs.plugins.kotlin.parcelize)
    id("meshtastic.kmp.jvm.android")
    id("meshtastic.koin")
}

kotlin {
    jvm()

    @Suppress("UnstableApiUsage")
    android {
        androidResources.enable = false
        withHostTest { isIncludeAndroidResources = true }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.javax.inject)
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.datetime)
            api(libs.okio)
            implementation(libs.kermit)
        }
        androidMain.dependencies {
            api(libs.androidx.core.ktx)
            api(libs.nordic.common.core)
        }
        commonTest.dependencies { implementation(libs.kotlinx.coroutines.test) }
    }
}
