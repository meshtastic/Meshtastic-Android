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
package org.meshtastic.feature.map.maplibre.terrain

import okio.Path

/**
 * Where this platform's offline terrain (Mapterhorn tiles plus [OfflineTerrainRegion]'s manifest) is kept.
 *
 * Mirrors [org.meshtastic.feature.map.layers.mapLayersDirectory]'s expect/actual shape — same reasoning: app-private
 * storage on Android, a home-directory folder on Desktop next to the existing `~/.meshtastic` data. Declared here
 * rather than in `:feature:map-terrain` (which owns [org.meshtastic.feature.map.terrain.TerrainTileStore] itself but
 * deliberately knows nothing about *where* a caller puts it) or `:feature:map` (shared by both flavors, and the Google
 * flavor's own offline-terrain storage convention is a separate piece of work, not this one's to decide).
 *
 * [org.meshtastic.feature.map.layers.mapLayerFileSystem] is reused for the [okio.FileSystem] — it is already
 * `FileSystem.SYSTEM` on every platform this module targets, and redeclaring the same expect/actual here would just be
 * a second name for it.
 */
expect fun terrainStorageDirectory(): Path
