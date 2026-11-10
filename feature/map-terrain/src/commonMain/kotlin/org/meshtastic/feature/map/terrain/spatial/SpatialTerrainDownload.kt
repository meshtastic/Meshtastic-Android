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
@file:Suppress("MagicNumber", "LongParameterList", "ReturnCount", "TooManyFunctions")

package org.meshtastic.feature.map.terrain.spatial

import org.meshtastic.feature.map.terrain.GeoBounds
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** How much of a box the downloaded terrain covers. */
object SpatialTerrainCoverage {
    /**
     * Fraction of [box] covered by any of [sources], sampled on an (n+1)×(n+1) lattice that includes the box's edges,
     * so a thin uncovered strip along an edge is never missed.
     */
    fun fraction(box: GeoBounds, sources: List<GeoBounds>, samples: Int = 24): Double {
        if (samples <= 0 || sources.isEmpty()) return 0.0
        // The last sample is the edge itself, not low + span, which can land a hair outside.
        fun position(index: Int, low: Double, high: Double) =
            if (index == samples) high else low + (high - low) * index / samples
        var hits = 0
        for (j in 0..samples) {
            val lat = position(j, box.south, box.north)
            for (i in 0..samples) {
                val lon = position(i, box.west, box.east)
                if (sources.any { lon >= it.west && lon <= it.east && lat >= it.south && lat <= it.north }) hits++
            }
        }
        return hits.toDouble() / ((samples + 1) * (samples + 1))
    }
}

/** Why the spatial map can't download its map for the whole box. */
enum class SpatialTerrainBlock {
    /** The download would push offline maps past the total storage limit. */
    STORAGE,

    /** Even the smallest area that covers the box is over the per-map limit. */
    TOO_LARGE,
}

/** Storage caps the download decision measures against. */
data class SpatialStorageLimits(
    val maxRegionBytes: Long = 512L * 1024 * 1024,
    val maxTotalBytes: Long = 3L * 1024 * 1024 * 1024,
)

/**
 * What the spatial map should do to keep its box covered by an offline map: streets and terrain. The app keeps one full
 * offline map of its own and grows or moves it as the mesh changes; the user's regions count as coverage but are never
 * changed.
 */
sealed interface SpatialTerrainAutoDecision {
    /** Covered, busy, or the backoff says not now. */
    data object None : SpatialTerrainAutoDecision

    /** No managed region yet: download one for this area. */
    data class Create(val area: GeoBounds) : SpatialTerrainAutoDecision

    /** Replace the managed region with one for this area. */
    data class Expand(val area: GeoBounds, val replacing: String) : SpatialTerrainAutoDecision

    data class Blocked(val reason: SpatialTerrainBlock) : SpatialTerrainAutoDecision

    /** The area to download, for [Create] and [Expand]. */
    val target: GeoBounds?
        get() =
            when (this) {
                is Create -> area

                is Expand -> area

                None,
                is Blocked,
                -> null
            }

    companion object {
        /** The owner tag the spatial map puts on the offline region it keeps. */
        const val MANAGER = "spatial-map"

        /** Every sample must be covered: the volume should show relief edge to edge. */
        const val COVERAGE_THRESHOLD = 1.0
        const val MAX_SPAN_METERS = 350_000.0

        /** Rough size of global z0-12 terrain per km². The manager re-checks the real plan. */
        const val TERRAIN_BYTES_PER_SQUARE_KM = 4_096L

        /** Rough size of the street map per km². Urban areas, so rural meshes come in well under it. */
        const val BASEMAP_BYTES_PER_SQUARE_KM = 4_096L

        /** Areas this small (per side) also get the regional high-resolution extract. */
        const val REGIONAL_MAX_SPAN_METERS = 20_000.0

        /**
         * [streetsTooLarge] is an area whose street map was over the per-map limit: inside it, terrain alone counts.
         */
        fun make(
            box: GeoBounds,
            regions: List<SpatialOfflineRegion>,
            isBusy: Boolean,
            backoff: SpatialTerrainBackoff,
            nowMillis: Long,
            growOnly: Boolean = false,
            streetsTooLarge: GeoBounds? = null,
            limits: SpatialStorageLimits = SpatialStorageLimits(),
        ): SpatialTerrainAutoDecision {
            val terrain = regions.filter { it.hasTerrain }.map { it.bounds }
            val streets = regions.filter { it.hasBasemap }.map { it.bounds }
            val hasTerrain = SpatialTerrainCoverage.fraction(box, terrain) >= COVERAGE_THRESHOLD
            val hasStreets =
                streetsTooLarge?.contains(box) == true ||
                    SpatialTerrainCoverage.fraction(box, streets) >= COVERAGE_THRESHOLD
            if ((hasTerrain && hasStreets) || isBusy) return None
            // Normally one. A second is left over when the app quit mid-download, or is terrain for a bigger area kept
            // beside a street map when the bigger street map was too large. Grow from the largest with terrain.
            val allManaged = regions.filter { it.managedBy == MANAGER }
            val managed =
                allManaged.filter { it.hasTerrain }.maxByOrNull { it.bounds.areaSquareKm() } ?: allManaged.firstOrNull()
            // The app's own check only grows a region the spatial map already made.
            if (growOnly && managed == null) return None
            // A managed region that already spans the box is missing streets or terrain (a download cut short):
            // download the same area again. Otherwise grow it to take in the box. If that gets too big (say the radio
            // is now on another continent), move it to the box; the bare box is last.
            val grown = managed?.let {
                if (it.bounds.contains(box)) it.bounds else it.bounds.union(box).expandedForTerrain()
            }
            val target =
                listOfNotNull(grown, box.expandedForTerrain(), box).firstOrNull { fits(it, limits) }
                    ?: return Blocked(SpatialTerrainBlock.TOO_LARGE)
            // Storage counts every region, less the managed ones that the download replaces.
            val totalBytes = regions.sumOf { it.totalByteCount }
            val freed = allManaged.sumOf { it.totalByteCount }
            if (totalBytes - freed + estimatedBytes(target) > limits.maxTotalBytes) {
                return Blocked(SpatialTerrainBlock.STORAGE)
            }
            if (!backoff.allows(target, nowMillis)) return None
            return if (managed != null) Expand(target, managed.id) else Create(target)
        }

        /**
         * The smaller areas that still cover [box], to try in turn when the street map for [target] is over the per-map
         * limit (the estimate is rough; the manager checks the real plan).
         */
        fun smallerAreas(target: GeoBounds, box: GeoBounds): List<GeoBounds> {
            val result = mutableListOf<GeoBounds>()
            for (area in listOf(box.expandedForTerrain(), box)) {
                if (area != target && target.contains(area) && area !in result) result += area
            }
            return result
        }

        fun estimatedTerrainBytes(bounds: GeoBounds): Long =
            bounds.areaSquareKm().toLong() * TERRAIN_BYTES_PER_SQUARE_KM

        fun estimatedBasemapBytes(bounds: GeoBounds): Long =
            bounds.areaSquareKm().toLong() * BASEMAP_BYTES_PER_SQUARE_KM

        /** Streets plus terrain. */
        fun estimatedBytes(bounds: GeoBounds): Long = estimatedBasemapBytes(bounds) + estimatedTerrainBytes(bounds)

        fun includesRegional(bounds: GeoBounds): Boolean {
            val (width, height) = bounds.sizeInMeters()
            return max(width, height) <= REGIONAL_MAX_SPAN_METERS
        }

        /**
         * The street map and the terrain each have to fit the per-map limit, as they do for the user's own downloads.
         */
        private fun fits(bounds: GeoBounds, limits: SpatialStorageLimits): Boolean {
            val (width, height) = bounds.sizeInMeters()
            return width <= MAX_SPAN_METERS &&
                height <= MAX_SPAN_METERS &&
                estimatedBasemapBytes(bounds) <= limits.maxRegionBytes &&
                estimatedTerrainBytes(bounds) <= limits.maxRegionBytes
        }
    }
}

/**
 * Retry rules for automatic downloads, per target area: a failure waits 10 min · 2^n before the next try and stops
 * after three; a cancel stops automatic tries for that area this session.
 */
data class SpatialTerrainBackoff(
    val target: GeoBounds? = null,
    val failures: Int = 0,
    val retryAfterMillis: Long? = null,
    val cancelled: Boolean = false,
) {
    fun allows(target: GeoBounds, nowMillis: Long): Boolean {
        if (target != this.target) return true
        if (gaveUp(target)) return false
        return retryAfterMillis == null || nowMillis >= retryAfterMillis
    }

    /** True when automatic downloads have stopped for this area (cancelled, or failed too often). */
    fun gaveUp(target: GeoBounds): Boolean = target == this.target && (cancelled || failures >= MAX_FAILURES)

    fun recordingFailure(target: GeoBounds, nowMillis: Long): SpatialTerrainBackoff {
        val base = if (target != this.target) SpatialTerrainBackoff(target = target) else this
        val count = base.failures + 1
        return base.copy(
            failures = count,
            retryAfterMillis = nowMillis + (BASE_DELAY_MILLIS * 2.0.pow(count - 1)).toLong(),
        )
    }

    fun recordingCancel(target: GeoBounds): SpatialTerrainBackoff =
        (if (target != this.target) SpatialTerrainBackoff(target = target) else this).copy(cancelled = true)

    companion object {
        const val BASE_DELAY_MILLIS = 600_000L
        const val MAX_FAILURES = 3
    }
}

/** Width and height in meters, measured across the middle of the box. */
fun GeoBounds.sizeInMeters(): Pair<Double, Double> {
    val frame = SpatialFrame(this)
    return (east - west) * frame.metersPerDegreeLongitude to (north - south) * SpatialFrame.METERS_PER_DEGREE_LATITUDE
}

fun GeoBounds.areaSquareKm(): Double {
    val (width, height) = sizeInMeters()
    return width * height / 1_000_000
}

fun GeoBounds.union(other: GeoBounds): GeoBounds = GeoBounds(
    south = min(south, other.south),
    west = min(west, other.west),
    north = max(north, other.north),
    east = max(east, other.east),
)

/**
 * Pads each side by max([minPadMeters], [ratio] × the box's size) and snaps outward to z10 tile edges, so small moves
 * of the mesh give the same area and nothing is downloaded again.
 */
fun GeoBounds.expandedForTerrain(ratio: Double = 0.15, minPadMeters: Double = 10_000.0): GeoBounds {
    val (width, height) = sizeInMeters()
    val frame = SpatialFrame(this)
    val padLon = max(minPadMeters, ratio * width) / frame.metersPerDegreeLongitude
    val padLat = max(minPadMeters, ratio * height) / SpatialFrame.METERS_PER_DEGREE_LATITUDE
    val n = 1024.0 // tiles per side at z10
    val epsilon = 1e-9
    val x0 = floor((max(west - padLon, -180.0) + 180) / 360 * n + epsilon)
    val x1 = ceil((min(east + padLon, 180.0) + 180) / 360 * n - epsilon)
    val yTop = floor(SpatialFrame.mercatorY(min(north + padLat, 85.0)) * n + epsilon)
    val yBottom = ceil(SpatialFrame.mercatorY(max(south - padLat, -85.0)) * n - epsilon)
    return GeoBounds(
        south = SpatialFrame.latitudeOfMercatorY(yBottom / n),
        west = x0 / n * 360 - 180,
        north = SpatialFrame.latitudeOfMercatorY(yTop / n),
        east = x1 / n * 360 - 180,
    )
}
