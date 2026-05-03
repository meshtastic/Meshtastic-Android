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
    alias(libs.plugins.meshtastic.kotlinx.serialization)
    id("meshtastic.kmp.jvm.android")
    id("meshtastic.koin")
}

kotlin {
    jvm()

    android {
        namespace = "org.meshtastic.core.data"
        androidResources.enable = false
        withHostTest { isIncludeAndroidResources = true }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.core.repository)
            implementation(projects.core.common)
            implementation(projects.core.database)
            implementation(projects.core.datastore)
            implementation(projects.core.di)
            implementation(projects.core.model)
            implementation(projects.core.network)
            implementation(projects.core.prefs)
            implementation(projects.core.proto)
            implementation(projects.core.takserver)

            implementation(libs.jetbrains.lifecycle.runtime)
            implementation(libs.androidx.paging.common)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kermit)
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.kotlinx.collections.immutable)
        }

        // Room / SQLite runtime shared between Android and Desktop JVM targets
        val jvmAndroidMain by getting {
            dependencies {
                implementation(libs.androidx.room.runtime)
                implementation(libs.androidx.room.paging)
                implementation(libs.androidx.sqlite.bundled)
            }
        }

        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.core.location.altitude)
        }

        commonTest.dependencies {
            implementation(projects.core.testing)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
