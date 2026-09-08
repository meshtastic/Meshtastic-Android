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
 * Placeholder Kotlin/Native implementation — same reason as [decodeTerrariumTile]'s nativeMain actual: this module has
 * no iOS surface, but the shared KMP convention plugin adds Kotlin/Native targets to every module regardless.
 */
actual class TerrainTileFetcher actual constructor(pmtilesUrl: String) : AutoCloseable {

    actual fun fetchTile(zoom: Int, x: Int, y: Int): ByteArray? = throw NotImplementedError(
        "Terrain tile fetching is not implemented for Kotlin/Native (iOS). " +
            "This module has no iOS surface; use the Android or JVM implementations instead.",
    )

    actual override fun close() = Unit
}
