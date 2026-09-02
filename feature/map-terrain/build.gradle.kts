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
plugins { alias(libs.plugins.meshtastic.kmp.feature) }

// Offline terrain: Terrarium elevation decode, hillshade shading, and contour-line generation —
// pure computation shared by both the Google flavor (androidApp/src/google, pre-rendered hillshade
// PNGs + Polyline contours) and the MapLibre flavor (feature/map-maplibre, native raster-dem
// hillshade + GeoJSON contours). No Compose UI and no rendering primitives live here; only math and
// the platform image decode it depends on. Only jvm()+android() are declared below — nothing here
// is consumed by a KMP-shared iOS surface — but the `meshtastic.kmp.feature` convention plugin adds
// Kotlin/Native (iOS) targets to every KMP module regardless, so `decodeTerrariumTile`'s expect/actual
// still needs a nativeMain actual; see its doc comment for why that one intentionally throws.
kotlin {
    jvm()

    @Suppress("UnstableApiUsage")
    android {
        namespace = "org.meshtastic.feature.map.terrain"
        androidResources.enable = false
        withHostTest { isIncludeAndroidResources = true }
    }

    sourceSets {
        commonMain.dependencies { implementation(projects.core.common) }

        // Skia's Image decoder reaches WebP directly; brought in transitively by Compose
        // Multiplatform's desktop UI artifact, which the `meshtastic.kmp.feature` convention plugin
        // already applies — see feature/map-maplibre's identical jvmTest dependency for precedent.
        jvmMain.dependencies { implementation(compose.desktop.currentOs) }
    }
}
