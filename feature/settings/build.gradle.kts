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
    alias(libs.plugins.meshtastic.kmp.feature)
    alias(libs.plugins.meshtastic.kotlinx.serialization)
    id("meshtastic.kmp.jvm.android")
}

kotlin {
    jvm()

    android {
        namespace = "org.meshtastic.feature.settings"
        androidResources.enable = false
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.data)
            implementation(projects.core.database)
            implementation(projects.core.datastore)
            implementation(projects.core.domain)
            implementation(projects.core.model)
            implementation(projects.core.navigation)
            implementation(projects.core.proto)
            implementation(projects.core.repository)
            implementation(projects.core.service)
            implementation(projects.core.resources)
            implementation(projects.core.ui)
            implementation(projects.core.di)
            implementation(projects.core.takserver)

            implementation(libs.kotlinx.collections.immutable)
            implementation(libs.aboutlibraries.compose.m3)
            implementation(libs.jetbrains.navigation3.ui)
        }

        androidMain.dependencies {
            implementation(projects.core.barcode)
            implementation(projects.core.nfc)
            implementation(libs.androidx.appcompat)
        }

        commonTest.dependencies {
            implementation(project(":core:datastore"))
            implementation(libs.compose.multiplatform.ui.test)
        }

        jvmTest.dependencies { implementation(compose.desktop.currentOs) }
    }
}
