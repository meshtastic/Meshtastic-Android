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
package org.meshtastic.feature.map.maplibre.geojson

/**
 * Property keys carried on node features. MapLibre styles nodes by reading these back out through feature expressions,
 * so every value the map needs to draw a node must be written here — a layer cannot reach into the ViewModel.
 */
object NodeFeatureKeys {
    const val NODE_NUM = "nodeNum"
    const val SHORT_NAME = "shortName"
    const val LONG_NAME = "longName"
    const val IS_FAVORITE = "isFavorite"
    const val IS_ONLINE = "isOnline"
    const val IS_SELF = "isSelf"
    const val FOREGROUND = "fg"
    const val BACKGROUND = "bg"
    const val LAST_HEARD = "lastHeard"
    const val PRECISION_METERS = "precisionMeters"
}
