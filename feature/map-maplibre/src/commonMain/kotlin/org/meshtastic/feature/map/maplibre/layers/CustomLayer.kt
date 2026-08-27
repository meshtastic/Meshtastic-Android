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
package org.meshtastic.feature.map.maplibre.layers

/**
 * A user-imported overlay, already reduced to GeoJSON.
 *
 * The host resolves whatever the user actually imported — a local file, a network URL, a KML archive — into one of
 * these; this module never touches the filesystem.
 *
 * @param uri a `file://`, `https://` or data URI MapLibre can fetch GeoJSON from.
 * @param refreshToken bumped by the host when the user asks to refresh this layer. A network layer keeps the same URI
 *   across a refresh, so without something that changes there is nothing to tell the map its contents moved on — see
 *   [CustomLayers][org.meshtastic.feature.map.maplibre.layers.CustomLayers].
 */
data class CustomLayer(val id: String, val uri: String, val refreshToken: Int = 0)
