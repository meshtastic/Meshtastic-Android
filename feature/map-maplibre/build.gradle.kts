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

// MapLibre map surfaces for the flavors that are not Google Maps: the F-Droid Android flavor and
// the desktop app. Deliberately a separate module rather than source sets inside `:feature:map` —
// that module compiles into BOTH Android flavors, so MapLibre living there would pull
// maplibre-native's .so payload into the Play Store build for no reason. Nothing here may be
// depended on by `androidApp`'s `google` flavor.
kotlin {
    jvm()

    @Suppress("UnstableApiUsage")
    android {
        namespace = "org.meshtastic.feature.map.maplibre"
        androidResources.enable = false
        withHostTest { isIncludeAndroidResources = true }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.data)
            implementation(projects.core.di)
            implementation(projects.core.model)
            implementation(projects.core.navigation)
            implementation(projects.core.prefs)
            implementation(projects.core.repository)
            implementation(projects.core.resources)
            implementation(projects.core.service)
            implementation(projects.core.ui)
            implementation(projects.feature.map)

            implementation(libs.kotlinx.collections.immutable)
            // Imported layers can name an icon image by URL; Coil fetches and decodes it.
            implementation(libs.coil)
            implementation(libs.coil.network.ktor3)
            implementation(libs.meshtastic.protobufs)

            api(libs.maplibre.compose)
            api(libs.maplibre.compose.material3)
        }

        // maplibre-compose 0.15.0 no longer brings the MapLibre Android SDK along transitively —
        // Android renders through maplibre-native FFI now, so the backend ships as its own
        // artifact. runtimeOnly because nothing compiles against it; it only has to reach the APK.
        androidMain.dependencies { runtimeOnly(libs.maplibre.compose.runtime.opengl.android) }
    }
}
