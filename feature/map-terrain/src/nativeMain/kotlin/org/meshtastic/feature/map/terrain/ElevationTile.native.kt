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
package org.meshtastic.feature.map.terrain

/**
 * Placeholder Kotlin/Native implementation of Terrarium WebP decoding.
 *
 * This module explicitly states "No iOS target" (see build.gradle.kts comment), but the KMP library convention plugin
 * adds iOS targets automatically to all KMP modules. Until those targets are explicitly excluded from the convention
 * plugin or this module restructured, iOS must be able to compile. This stub throws NotImplementedError if called.
 */
actual fun decodeTerrariumTile(webpBytes: ByteArray): ElevationTile = throw NotImplementedError(
    "Terrarium elevation decoding is not implemented for Kotlin/Native (iOS). " +
        "This module has no iOS surface; use the Android or JVM implementations instead.",
)
