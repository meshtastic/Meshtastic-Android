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
    alias(libs.plugins.meshtastic.kotlinx.serialization)
}

kotlin {
    // Desktop needs this module: MapScreen and the shared map view models are common code, and
    // :feature:map-maplibre renders them on the JVM target.
    jvm()

    android { withHostTest { isIncludeAndroidResources = true } }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.collections.immutable)
            // KML import parses through the same xmlutil the app already resolves for CoT XML.
            implementation(libs.xmlutil.core)
            implementation(projects.core.data)
            implementation(projects.core.database)
            implementation(projects.core.datastore)
            implementation(projects.core.model)
            implementation(projects.core.navigation)
            implementation(projects.core.prefs)
            implementation(projects.core.repository)
            implementation(libs.meshtastic.protobufs)
            implementation(projects.core.service)
            implementation(projects.core.resources)
            implementation(projects.core.ui)
            implementation(projects.core.di)
            // The imported-layer store: Okio for the files it keeps, Ktor for the network layers it fetches.
            implementation(libs.okio)
            implementation(libs.ktor.client.core)
        }
    }
}
