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
package org.meshtastic.feature.coverage

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import org.koin.compose.koinInject
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.feature.map.component.SitePlannerParams
import org.meshtastic.feature.map.terrain.TerrainTileStore
import kotlin.math.log10

/**
 * Computes a Site Planner estimate on this device: Mapterhorn terrain for the coverage disc, cached under [store], then
 * the ITU-R P.1812 sweep, returned as the styled GeoJSON iso-bands a map layer takes.
 *
 * Throws when terrain can't be fetched or a value is outside what P.1812 accepts. Cancellation propagates.
 */
suspend fun estimateCoverageGeoJson(
    params: SitePlannerParams,
    store: TerrainTileStore,
    dispatcher: CoroutineDispatcher,
): String = withContext(dispatcher) {
    val site = params.toSite()
    // The bounds let the zoom drop for a wide disc, so its tiles fit the decoded cache.
    MapterhornElevation(bounds = site.coverageBounds(), store = store).use { source ->
        source.prefetch(site)
        LocalCoverage(source).sweepGrid(site).toGeoJson(params.toCoverageStyle())
    }
}

/** [estimateCoverageGeoJson] bound to the app's compute dispatcher and its coverage terrain cache. */
@Composable
fun rememberCoverageEstimate(): suspend (SitePlannerParams) -> String {
    val dispatchers: CoroutineDispatchers = koinInject()
    return remember(dispatchers) {
        val store = TerrainTileStore(coverageFileSystem(), coverageTerrainDirectory())
        val estimate: suspend (SitePlannerParams) -> String = { params ->
            estimateCoverageGeoJson(params, store, dispatchers.default)
        }
        estimate
    }
}

/**
 * Where coverage terrain is cached between launches. It's kept apart from downloaded offline map regions so a coverage
 * estimate never changes the size and tile count a region reports.
 */
internal expect fun coverageTerrainDirectory(): Path

/** The platform file system the terrain cache lives on; common okio has no `FileSystem.SYSTEM`. */
internal expect fun coverageFileSystem(): FileSystem

/** The planner form's transmitter as the coverage model's site. */
internal fun SitePlannerParams.toSite(): Site = Site(
    name = name,
    latitude = latitude,
    longitude = longitude,
    frequencyMhz = txFreqMhz,
    txPowerDbm = wattsToDbm(txPowerWatts),
    rxSensitivityDbm = rxSensitivityDbm,
    txHeightM = txHeightMeters,
    rxHeightM = rxHeightMeters,
    txGainDbi = txGainDbi,
    radiusKm = maxRangeKm,
)

/** The form's Display section: palette, the dBm range the ramp spans, and overlay transparency. */
internal fun SitePlannerParams.toCoverageStyle(): CoverageStyle = CoverageStyle.fromTransparency(
    palette = colorScale,
    minDbm = minDbm,
    maxDbm = maxDbm,
    transparencyPercent = overlayTransparency,
)

internal fun wattsToDbm(watts: Double): Double {
    require(watts > 0.0) { "transmit power must be positive, got $watts W" }
    return DBM_PER_DECADE * log10(watts * MILLIWATTS_PER_WATT)
}

private const val DBM_PER_DECADE = 10.0
private const val MILLIWATTS_PER_WATT = 1000.0
