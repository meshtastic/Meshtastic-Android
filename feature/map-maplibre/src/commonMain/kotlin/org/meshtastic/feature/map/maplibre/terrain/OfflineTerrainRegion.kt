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

import kotlinx.serialization.Serializable
import org.maplibre.spatialk.geojson.BoundingBox
import org.maplibre.spatialk.geojson.Position
import org.meshtastic.feature.map.terrain.GeoBounds

/**
 * What one downloaded offline-terrain region covers, persisted alongside its tiles as `manifest.json`.
 *
 * Deliberately singular — [OfflineTerrainRepository] tracks at most one region at a time, replaced wholesale by the
 * next download. Unlike the base map's own offline packs (which can be stacked, one per neighborhood, and MapLibre's
 * native `OfflineManager` already carries that bookkeeping) terrain has no equivalent native API to lean on, and
 * nothing in the F-Droid/Desktop UX yet asks for more than "the terrain around where I am" — see
 * [OfflineTerrainRepository]'s own doc comment for the fuller reasoning. Multi-region support, if ever needed, is an
 * additive change: give this a generated `id` and the manifest a list.
 */
@Serializable
data class OfflineTerrainRegion(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
    val maxZoom: Int,
    val hasRegionalDetail: Boolean,
    val tileCount: Long,
    val byteSize: Long,
) {
    val bounds: GeoBounds
        get() = GeoBounds(south = south, west = west, north = north, east = east)
}

/** [bounds] as the manifest's flat lat/lon fields — the inverse of [OfflineTerrainRegion.bounds]. */
internal fun GeoBounds.toBoundingBox(): BoundingBox = BoundingBox(
    southwest = Position(longitude = west, latitude = south),
    northeast = Position(longitude = east, latitude = north),
)

/** A MapLibre viewport box as this module's own [GeoBounds] — the type `feature/map-terrain`'s math works in. */
internal fun BoundingBox.toGeoBounds(): GeoBounds = GeoBounds(south = south, west = west, north = north, east = east)

/**
 * Whether [bounds] overlaps this region at all.
 *
 * Intersection, not containment: a viewport straddling the region's edge still gets partial hillshade/contours rather
 * than none, which reads better than a hard cutoff mid-screen. The tradeoff — tiles just outside the downloaded
 * footprint are requested and may be missing — is the same `file://`-missing-tile risk already flagged for the whole
 * offline-terrain rendering path; see this feature's PR description.
 */
internal fun OfflineTerrainRegion.intersects(bounds: BoundingBox): Boolean =
    south <= bounds.north && north >= bounds.south && west <= bounds.east && east >= bounds.west
