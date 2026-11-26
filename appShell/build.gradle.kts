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
    alias(libs.plugins.meshtastic.kmp.web)
    alias(libs.plugins.meshtastic.kmp.library.compose)
}

kotlin {
    android { namespace = "org.meshtastic.appshell" }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.model)
            implementation(projects.core.navigation)
            implementation(projects.core.repository)
            implementation(projects.core.service)
            implementation(projects.core.ui)

            // The feature modules with a web target. The map, which has none, is in nonWebMain.
            implementation(projects.feature.connections)
            implementation(projects.feature.discovery)
            implementation(projects.feature.docs)
            implementation(projects.feature.firmware)
            implementation(projects.feature.messaging)
            implementation(projects.feature.node)
            implementation(projects.feature.settings)
            implementation(projects.feature.wifiProvision)

            implementation(libs.compose.multiplatform.foundation)
            implementation(libs.compose.multiplatform.runtime)
            implementation(libs.compose.multiplatform.ui)
            implementation(libs.jetbrains.lifecycle.runtime.compose)
            implementation(libs.jetbrains.navigation3.ui)
            implementation(libs.koin.compose.viewmodel)
        }

        getByName("nonWebMain").dependencies { implementation(projects.feature.map) }
    }
}
