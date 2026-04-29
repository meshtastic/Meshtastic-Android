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
package org.meshtastic.core.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import org.meshtastic.core.ui.component.PlaceholderScreen

/**
 * Provides an embeddable discovery map composable that renders discovered node markers and topology polylines for a
 * Local Mesh Discovery scan session. Unlike [LocalMapViewProvider], this does **not** include node clustering,
 * waypoints, location tracking, or any main-map features — it is designed to be embedded inside the discovery summary
 * scaffold.
 *
 * Parameters:
 * - `userLatitude` / `userLongitude`: The scanner's position at scan time (orange marker).
 * - `nodes`: Platform-neutral [DiscoveryMapNode] list for marker placement and styling.
 * - `modifier`: Compose modifier for the map.
 *
 * On Desktop/JVM targets where native maps are not yet available, it falls back to a [PlaceholderScreen].
 */
@Suppress("Wrapping")
val LocalDiscoveryMapProvider =
    compositionLocalOf<
        @Composable (
            userLatitude: Double,
            userLongitude: Double,
            nodes: List<DiscoveryMapNode>,
            modifier: Modifier,
        ) -> Unit,
        > {
        { _, _, _, _ -> PlaceholderScreen("Discovery Map") }
    }
