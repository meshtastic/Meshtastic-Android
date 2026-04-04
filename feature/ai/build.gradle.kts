/*
 * Copyright (c) 2026 Chris7X
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
    alias(libs.plugins.meshtastic.kotlinx.serialization)
}

kotlin {
    jvm()

    @Suppress("UnstableApiUsage")
    android {
        namespace = "org.meshtastic.feature.ai"
        // Enable androidResources for noCompress configuration (LiteRT-LM memory-maps .litertlm)
        androidResources {
            enable = true
            noCompress += "litertlm"
        }
        withHostTest { isIncludeAndroidResources = true }
        // Assets are automatically picked up from src/androidMain/assets in KMP
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.data)
            implementation(projects.core.datastore)
            implementation(projects.core.model)
            implementation(projects.core.navigation)
            implementation(projects.core.proto)
            implementation(projects.core.repository)
            implementation(projects.core.service)
            implementation(projects.core.ui)
            implementation(projects.core.di)

            implementation(libs.kotlinx.collections.immutable)
        }

        androidMain.dependencies {
            // LiteRT (former TFLite) SDK for on-device ML/AI inference
            implementation(libs.google.litert.runtime)
        }

        androidUnitTest.dependencies {
            implementation(libs.junit)
            implementation(libs.robolectric)
            implementation(libs.turbine)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.androidx.test.ext.junit)
        }

        commonTest.dependencies {
            implementation(project(":core:testing"))
        }
    }
}
