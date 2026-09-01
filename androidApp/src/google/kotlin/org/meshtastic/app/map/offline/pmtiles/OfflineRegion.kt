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
package org.meshtastic.app.map.offline.pmtiles

import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import kotlinx.serialization.Serializable

/**
 * A downloaded, ready-to-render offline vector region. Its tiles live in [OfflineRegionStore.archiveFile].
 *
 * Public, not `internal`, purely so [org.meshtastic.app.map.MapViewModel]'s `offlineRegions`/`offlineRegionCovering`
 * (also public — every other `MapViewModel` property is, and there's no reason for these alone to differ) can expose it
 * without Kotlin's "public declaration exposes internal type" check. Everything that actually builds or mutates one —
 * [OfflineRegionExtractor], [OfflineRegionStore] — stays `internal`.
 */
@Serializable
data class OfflineRegion(
    val id: String,
    val southLat: Double,
    val westLon: Double,
    val northLat: Double,
    val eastLon: Double,
    val minZoom: Int,
    val maxZoom: Int,
    val tileCount: Long,
    val byteSize: Long,
    val createdAtEpochSeconds: Long,
) {
    val bounds: LatLngBounds
        get() = LatLngBounds(LatLng(southLat, westLon), LatLng(northLat, eastLon))

    val zoomRange: IntRange
        get() = minZoom..maxZoom
}
