/*
 * Copyright (c) 2025 Meshtastic LLC
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

package org.meshtastic.feature.map.maplibre.utils

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import timber.log.Timber
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Manages tile caching for "hot areas" - frequently visited map regions.
 * Automatically caches tiles for areas where users spend time viewing the map.
 */
class MapLibreTileCacheManager(private val context: Context) {
    // Lazy initialization - only create OfflineManager after MapLibre is initialized
    private val offlineManager: OfflineManager by lazy { OfflineManager.getInstance(context) }
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val PREFS_NAME = "maplibre_tile_cache"
        private const val KEY_HOT_AREAS = "hot_areas"
        private const val KEY_UPDATE_INTERVAL_MS = "update_interval_ms"
        private const val KEY_MAX_CACHE_SIZE_MB = "max_cache_size_mb"
        private const val KEY_HOT_AREA_THRESHOLD_SEC = "hot_area_threshold_sec"
        private const val KEY_MIN_ZOOM = "min_zoom"
        private const val KEY_MAX_ZOOM = "max_zoom"

        // Default values
        private const val DEFAULT_UPDATE_INTERVAL_MS = 7L * 24 * 60 * 60 * 1000 // 7 days
        private const val DEFAULT_MAX_CACHE_SIZE_MB = 500L // 500 MB
        private const val DEFAULT_HOT_AREA_THRESHOLD_SEC = 60L // 1 minute
        private const val DEFAULT_MIN_ZOOM = 10.0
        private const val DEFAULT_MAX_ZOOM = 16.0

        private const val JSON_FIELD_REGION_NAME = "name"
        private const val JSON_FIELD_REGION_ID = "id"
        private const val JSON_FIELD_CENTER_LAT = "centerLat"
        private const val JSON_FIELD_CENTER_LON = "centerLon"
        private const val JSON_FIELD_ZOOM = "zoom"
        private const val JSON_FIELD_LAST_VISIT = "lastVisit"
        private const val JSON_FIELD_VISIT_COUNT = "visitCount"
        private const val JSON_FIELD_TOTAL_TIME_SEC = "totalTimeSec"
        private const val JSON_CHARSET = "UTF-8"
    }

    data class HotArea(
        val id: String,
        val centerLat: Double,
        val centerLon: Double,
        val zoom: Double,
        val bounds: LatLngBounds,
        var lastVisit: Long,
        var visitCount: Int,
        var totalTimeSec: Long,
        var offlineRegionId: Long? = null,
    )

    /**
     * Records a camera position/viewport as a potential hot area.
     * If the user spends enough time in this area, it will be cached.
     */
    fun recordViewport(
        bounds: LatLngBounds,
        zoom: Double,
        styleUrl: String,
    ) {
        val centerLat = (bounds.latitudeNorth + bounds.latitudeSouth) / 2.0
        val centerLon = (bounds.longitudeEast + bounds.longitudeWest) / 2.0

        Timber.tag("MapLibreTileCacheManager").d("recordViewport called: center=[%.4f,%.4f], zoom=%.2f", centerLat, centerLon, zoom)

        // Find existing hot area within threshold (same general location)
        val existingArea = findHotArea(centerLat, centerLon, zoom)
        val now = System.currentTimeMillis()

        Timber.tag("MapLibreTileCacheManager").d("findHotArea result: ${if (existingArea != null) "found area ${existingArea.id.take(8)}" else "no match, creating new"}")

        if (existingArea != null) {
            // Update existing area - track actual elapsed time since last visit
            val timeSinceLastVisit = (now - existingArea.lastVisit) / 1000 // Convert to seconds
            existingArea.lastVisit = now
            existingArea.visitCount++
            // Add actual elapsed time (capped at 5 seconds per call to avoid huge jumps if app was backgrounded)
            existingArea.totalTimeSec += minOf(timeSinceLastVisit, 5)

            // Check if threshold is met and region doesn't exist yet
            val thresholdSec = getHotAreaThresholdSec()
            if (existingArea.totalTimeSec >= thresholdSec && existingArea.offlineRegionId == null) {
                Timber.tag("MapLibreTileCacheManager").d(
                    "Hot area threshold met (${existingArea.totalTimeSec}s >= ${thresholdSec}s), creating offline region: ${existingArea.id}",
                )
                createOfflineRegionForHotArea(existingArea, styleUrl)
            } else {
                Timber.tag("MapLibreTileCacheManager").d(
                    "Hot area progress: ${existingArea.totalTimeSec}s / ${thresholdSec}s (area: ${existingArea.id.take(8)})",
                )
            }
            saveHotAreas()
        } else {
            // Create new hot area
            Timber.tag("MapLibreTileCacheManager").d("Creating new hot area: center=[%.4f,%.4f], zoom=%.2f", centerLat, centerLon, zoom)
            val newArea = HotArea(
                id = UUID.randomUUID().toString(),
                centerLat = centerLat,
                centerLon = centerLon,
                zoom = zoom,
                bounds = bounds,
                lastVisit = now,
                visitCount = 1,
                totalTimeSec = 1,
            )
            addHotArea(newArea)
        }
    }

    /**
     * Manually caches the current viewport immediately, bypassing the hot area threshold.
     * Useful for "Cache this area now" functionality.
     */
    fun cacheCurrentArea(
        bounds: LatLngBounds,
        zoom: Double,
        styleUrl: String,
    ) {
        val centerLat = (bounds.latitudeNorth + bounds.latitudeSouth) / 2.0
        val centerLon = (bounds.longitudeEast + bounds.longitudeWest) / 2.0

        // Check if this area is already cached
        val existingArea = findHotArea(centerLat, centerLon, zoom)
        val now = System.currentTimeMillis()

        if (existingArea != null && existingArea.offlineRegionId != null) {
            // Already cached, just update visit info
            existingArea.lastVisit = now
            existingArea.visitCount++
            saveHotAreas()
            Timber.tag("MapLibreTileCacheManager").d("Area already cached: ${existingArea.id}")
            return
        }

        // Create or update hot area and immediately cache it
        val area = if (existingArea != null) {
            existingArea.apply {
                lastVisit = now
                visitCount++
                totalTimeSec = getHotAreaThresholdSec() // Set to threshold to ensure caching
            }
        } else {
            HotArea(
                id = UUID.randomUUID().toString(),
                centerLat = centerLat,
                centerLon = centerLon,
                zoom = zoom,
                bounds = bounds,
                lastVisit = now,
                visitCount = 1,
                totalTimeSec = getHotAreaThresholdSec(), // Set to threshold to ensure caching
            ).also { addHotArea(it) }
        }

        // Immediately create offline region
        if (area.offlineRegionId == null) {
            Timber.tag("MapLibreTileCacheManager").d("Manually caching area: ${area.id}")
            createOfflineRegionForHotArea(area, styleUrl)
        }

        saveHotAreas()
    }

    /**
     * Finds a hot area near the given coordinates and zoom level
     */
    private fun findHotArea(lat: Double, lon: Double, zoom: Double): HotArea? {
        val areas = loadHotAreas()
        val zoomDiffThreshold = 2.0 // Within 2 zoom levels

        Timber.tag("MapLibreTileCacheManager").d("findHotArea: searching ${areas.size} areas for lat=%.4f, lon=%.4f, zoom=%.2f", lat, lon, zoom)

        val result = areas.firstOrNull { area ->
            val latDiff = abs(area.centerLat - lat)
            val lonDiff = abs(area.centerLon - lon)
            val zoomDiff = abs(area.zoom - zoom)

            // Within ~1km and similar zoom level
            val matches = latDiff < 0.01 && lonDiff < 0.01 && zoomDiff < zoomDiffThreshold
            if (matches) {
                Timber.tag("MapLibreTileCacheManager").d("  Match found: area ${area.id.take(8)}, latDiff=%.4f, lonDiff=%.4f, zoomDiff=%.2f", latDiff, lonDiff, zoomDiff)
            }
            matches
        }

        if (result == null && areas.isNotEmpty()) {
            Timber.tag("MapLibreTileCacheManager").d("  No match. Closest area: lat=%.4f, lon=%.4f, zoom=%.2f", areas[0].centerLat, areas[0].centerLon, areas[0].zoom)
        }

        return result
    }

    /**
     * Creates an offline region for a hot area
     */
    private fun createOfflineRegionForHotArea(area: HotArea, styleUrl: String) {
        // Validate style URL - must be a valid HTTP/HTTPS URL ending in .json
        if (!styleUrl.startsWith("http://") && !styleUrl.startsWith("https://")) {
            Timber.tag("MapLibreTileCacheManager").e("Invalid style URL (must be HTTP/HTTPS): $styleUrl")
            return
        }
        if (!styleUrl.endsWith(".json") && !styleUrl.contains("style.json")) {
            Timber.tag("MapLibreTileCacheManager").w("Style URL may not be valid (should end in .json or contain style.json): $styleUrl")
        }

        // Validate bounds
        val bounds = area.bounds
        if (bounds.latitudeNorth <= bounds.latitudeSouth) {
            Timber.tag("MapLibreTileCacheManager").e("Invalid bounds: north (%.4f) must be > south (%.4f)", bounds.latitudeNorth, bounds.latitudeSouth)
            return
        }
        if (bounds.longitudeEast <= bounds.longitudeWest) {
            Timber.tag("MapLibreTileCacheManager").e("Invalid bounds: east (%.4f) must be > west (%.4f)", bounds.longitudeEast, bounds.longitudeWest)
            return
        }
        if (bounds.latitudeNorth > 90.0 || bounds.latitudeSouth < -90.0) {
            Timber.tag("MapLibreTileCacheManager").e("Invalid bounds: latitude out of range [%.4f, %.4f]", bounds.latitudeSouth, bounds.latitudeNorth)
            return
        }
        if (bounds.longitudeEast > 180.0 || bounds.longitudeWest < -180.0) {
            Timber.tag("MapLibreTileCacheManager").e("Invalid bounds: longitude out of range [%.4f, %.4f]", bounds.longitudeWest, bounds.longitudeEast)
            return
        }

        val minZoom = getMinZoom()
        val maxZoom = getMaxZoom()
        if (minZoom < 0 || maxZoom < minZoom) {
            Timber.tag("MapLibreTileCacheManager").e("Invalid zoom range: min=%.1f, max=%.1f", minZoom, maxZoom)
            return
        }

        val pixelRatio = context.resources.displayMetrics.density
        if (pixelRatio <= 0) {
            Timber.tag("MapLibreTileCacheManager").e("Invalid pixel ratio: %.2f", pixelRatio)
            return
        }

        Timber.tag("MapLibreTileCacheManager").d("Creating offline region: styleUrl=$styleUrl, bounds=[%.4f,%.4f,%.4f,%.4f], zoom=[%.1f-%.1f], pixelRatio=%.2f",
            bounds.latitudeNorth, bounds.latitudeSouth,
            bounds.longitudeEast, bounds.longitudeWest,
            minZoom, maxZoom, pixelRatio)

        try {
            val definition = OfflineTilePyramidRegionDefinition(
                styleUrl,
                bounds,
                minZoom,
                maxZoom,
                pixelRatio,
            )

            val metadata = encodeHotAreaMetadata(area)

            offlineManager.createOfflineRegion(
                definition,
                metadata,
                object : OfflineManager.CreateOfflineRegionCallback {
                    override fun onCreate(offlineRegion: OfflineRegion) {
                        Timber.tag("MapLibreTileCacheManager").d("Offline region created: ${offlineRegion.id} for hot area: ${area.id}")
                        area.offlineRegionId = offlineRegion.id
                        saveHotAreas()

                        // Start download
                        startDownload(offlineRegion)
                    }

                    override fun onError(error: String) {
                        Timber.tag("MapLibreTileCacheManager").e("Failed to create offline region: $error")
                    }
                },
            )
        } catch (e: Exception) {
            Timber.tag("MapLibreTileCacheManager").e(e, "Exception creating offline region: ${e.message}")
        }
    }

    /**
     * Starts downloading tiles for an offline region
     */
    private fun startDownload(region: OfflineRegion) {
        try {
            region.setObserver(object : OfflineRegion.OfflineRegionObserver {
                override fun onStatusChanged(status: OfflineRegionStatus) {
                    try {
                        val percentage = if (status.requiredResourceCount > 0) {
                            (100.0 * status.completedResourceCount / status.requiredResourceCount).toInt()
                        } else {
                            0
                        }

                        Timber.tag("MapLibreTileCacheManager").d(
                            "Offline region ${region.id} progress: $percentage% " +
                                "(${status.completedResourceCount}/${status.requiredResourceCount})",
                        )

                        if (status.isComplete) {
                            Timber.tag("MapLibreTileCacheManager").d("Offline region ${region.id} download complete")
                            region.setObserver(null)
                        }
                    } catch (e: Exception) {
                        Timber.tag("MapLibreTileCacheManager").e(e, "Error in onStatusChanged: ${e.message}")
                        // Remove observer on error to prevent further callbacks
                        try {
                            region.setObserver(null)
                        } catch (ex: Exception) {
                            Timber.tag("MapLibreTileCacheManager").e(ex, "Error removing observer: ${ex.message}")
                        }
                    }
                }

                override fun onError(error: OfflineRegionError) {
                    Timber.tag("MapLibreTileCacheManager").e("Offline region ${region.id} error: reason=${error.reason}, message=${error.message}")
                    // Remove observer on error to prevent further callbacks
                    try {
                        region.setObserver(null)
                        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    } catch (e: Exception) {
                        Timber.tag("MapLibreTileCacheManager").e(e, "Error handling offline region error: ${e.message}")
                    }
                }

                override fun mapboxTileCountLimitExceeded(limit: Long) {
                    Timber.tag("MapLibreTileCacheManager").w("Tile count limit exceeded: $limit")
                    // Remove observer on error to prevent further callbacks
                    try {
                        region.setObserver(null)
                        region.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    } catch (e: Exception) {
                        Timber.tag("MapLibreTileCacheManager").e(e, "Error handling tile limit exceeded: ${e.message}")
                    }
                }
            })

            region.setDownloadState(OfflineRegion.STATE_ACTIVE)
        } catch (e: Exception) {
            Timber.tag("MapLibreTileCacheManager").e(e, "Exception starting download for region ${region.id}: ${e.message}")
        }
    }

    /**
     * Updates all cached regions by invalidating their ambient cache
     */
    suspend fun updateCachedRegions() = withContext(Dispatchers.IO) {
        Timber.tag("MapLibreTileCacheManager").d("Updating cached regions...")

        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                if (offlineRegions == null || offlineRegions.isEmpty()) {
                    Timber.tag("MapLibreTileCacheManager").d("No offline regions to update")
                    return
                }

                Timber.tag("MapLibreTileCacheManager").d("Found ${offlineRegions.size} offline regions to update")

                offlineRegions.forEach { region ->
                    // Invalidate ambient cache to force re-download
                    offlineManager.invalidateAmbientCache(object : OfflineManager.FileSourceCallback {
                        override fun onSuccess() {
                            Timber.tag("MapLibreTileCacheManager").d("Invalidated cache for region ${region.id}")
                            // Resume download to update tiles
                            region.setDownloadState(OfflineRegion.STATE_ACTIVE)
                        }

                        override fun onError(message: String) {
                            Timber.tag("MapLibreTileCacheManager").e("Failed to invalidate cache: $message")
                        }
                    })
                }
            }

            override fun onError(error: String) {
                Timber.tag("MapLibreTileCacheManager").e("Failed to list offline regions: $error")
            }
        })
    }

    /**
     * Gets the total cache size in bytes
     */
    suspend fun getCacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        // MapLibre doesn't expose cache size directly, so we estimate based on regions
        // This is a rough approximation
        var totalSize = 0L
        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                if (offlineRegions != null) {
                    offlineRegions.forEach { region ->
                        // Estimate ~100KB per region (very rough)
                        totalSize += 100 * 1024
                    }
                }
            }

            override fun onError(error: String) {
                Timber.tag("MapLibreTileCacheManager").e("Failed to list offline regions: $error")
            }
        })
        totalSize
    }

    /**
     * Clears all cached regions
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        Timber.tag("MapLibreTileCacheManager").d("Clearing cache...")

        offlineManager.listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                if (offlineRegions == null || offlineRegions.isEmpty()) {
                    Timber.tag("MapLibreTileCacheManager").d("No offline regions to clear")
                    return
                }

                offlineRegions.forEach { region ->
                    region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                        override fun onDelete() {
                            Timber.tag("MapLibreTileCacheManager").d("Deleted offline region ${region.id}")
                        }

                        override fun onError(error: String) {
                            Timber.tag("MapLibreTileCacheManager").e("Failed to delete region ${region.id}: $error")
                        }
                    })
                }

                // Clear hot areas
                clearHotAreas()
            }

            override fun onError(error: String) {
                Timber.tag("MapLibreTileCacheManager").e("Failed to list offline regions: $error")
            }
        })
    }

    /**
     * Gets list of all hot areas
     */
    fun getHotAreas(): List<HotArea> {
        // Invalidate cache to ensure we get the latest from SharedPreferences
        hotAreasCache = null
        return loadHotAreas()
    }

    // Preferences getters/setters
    fun getUpdateIntervalMs(): Long = prefs.getLong(KEY_UPDATE_INTERVAL_MS, DEFAULT_UPDATE_INTERVAL_MS)
    fun setUpdateIntervalMs(intervalMs: Long) = prefs.edit().putLong(KEY_UPDATE_INTERVAL_MS, intervalMs).apply()

    fun getMaxCacheSizeMb(): Long = prefs.getLong(KEY_MAX_CACHE_SIZE_MB, DEFAULT_MAX_CACHE_SIZE_MB)
    fun setMaxCacheSizeMb(sizeMb: Long) = prefs.edit().putLong(KEY_MAX_CACHE_SIZE_MB, sizeMb).apply()

    fun getHotAreaThresholdSec(): Long = prefs.getLong(KEY_HOT_AREA_THRESHOLD_SEC, DEFAULT_HOT_AREA_THRESHOLD_SEC)
    fun setHotAreaThresholdSec(thresholdSec: Long) = prefs.edit().putLong(KEY_HOT_AREA_THRESHOLD_SEC, thresholdSec).apply()

    fun getMinZoom(): Double = prefs.getFloat(KEY_MIN_ZOOM, DEFAULT_MIN_ZOOM.toFloat()).toDouble()
    fun setMinZoom(zoom: Double) = prefs.edit().putFloat(KEY_MIN_ZOOM, zoom.toFloat()).apply()

    fun getMaxZoom(): Double = prefs.getFloat(KEY_MAX_ZOOM, DEFAULT_MAX_ZOOM.toFloat()).toDouble()
    fun setMaxZoom(zoom: Double) = prefs.edit().putFloat(KEY_MAX_ZOOM, zoom.toFloat()).apply()

    // Hot area persistence
    private fun loadHotAreas(): MutableList<HotArea> {
        if (hotAreasCache != null) {
            return hotAreasCache!!
        }
        val json = prefs.getString(KEY_HOT_AREAS, "[]") ?: "[]"
        return try {
            val jsonArray = gson.fromJson(json, Array<HotAreaJson>::class.java)
            val areas = jsonArray.map { json ->
                HotArea(
                    id = json.id,
                    centerLat = json.centerLat,
                    centerLon = json.centerLon,
                    zoom = json.zoom,
                    bounds = LatLngBounds.from(
                    json.boundsNorth,
                    json.boundsEast,
                    json.boundsSouth,
                    json.boundsWest,
                ),
                    lastVisit = json.lastVisit,
                    visitCount = json.visitCount,
                    totalTimeSec = json.totalTimeSec,
                    offlineRegionId = json.offlineRegionId,
                )
            }.toMutableList()
            hotAreasCache = areas
            Timber.tag("MapLibreTileCacheManager").d("Loaded ${areas.size} hot areas from SharedPreferences")
            areas
        } catch (e: Exception) {
            Timber.tag("MapLibreTileCacheManager").e(e, "Failed to load hot areas")
            mutableListOf<HotArea>().also { hotAreasCache = it }
        }
    }

    // In-memory cache of hot areas to avoid reloading from SharedPreferences every time
    private var hotAreasCache: MutableList<HotArea>? = null

    private fun saveHotAreas() {
        val areas = hotAreasCache ?: loadHotAreas()
        val jsonArray = areas.map { area ->
            HotAreaJson(
                id = area.id,
                centerLat = area.centerLat,
                centerLon = area.centerLon,
                zoom = area.zoom,
                boundsNorth = area.bounds.latitudeNorth,
                boundsSouth = area.bounds.latitudeSouth,
                boundsEast = area.bounds.longitudeEast,
                boundsWest = area.bounds.longitudeWest,
                lastVisit = area.lastVisit,
                visitCount = area.visitCount,
                totalTimeSec = area.totalTimeSec,
                offlineRegionId = area.offlineRegionId,
            )
        }
        val json = gson.toJson(jsonArray)
        prefs.edit().putString(KEY_HOT_AREAS, json).apply()
        Timber.tag("MapLibreTileCacheManager").d("Saved ${areas.size} hot areas to SharedPreferences")
    }

    private fun addHotArea(area: HotArea) {
        val areas = hotAreasCache ?: loadHotAreas()
        areas.add(area)
        hotAreasCache = areas
        saveHotAreas()
    }

    private fun clearHotAreas() {
        hotAreasCache = null
        prefs.edit().remove(KEY_HOT_AREAS).apply()
    }

    private fun encodeHotAreaMetadata(area: HotArea): ByteArray {
        val jsonObject = JsonObject()
        jsonObject.addProperty(JSON_FIELD_REGION_NAME, "Hot Area: ${area.id.take(8)}")
        jsonObject.addProperty(JSON_FIELD_REGION_ID, area.id)
        jsonObject.addProperty(JSON_FIELD_CENTER_LAT, area.centerLat)
        jsonObject.addProperty(JSON_FIELD_CENTER_LON, area.centerLon)
        jsonObject.addProperty(JSON_FIELD_ZOOM, area.zoom)
        jsonObject.addProperty(JSON_FIELD_LAST_VISIT, area.lastVisit)
        jsonObject.addProperty(JSON_FIELD_VISIT_COUNT, area.visitCount)
        jsonObject.addProperty(JSON_FIELD_TOTAL_TIME_SEC, area.totalTimeSec)
        return jsonObject.toString().toByteArray(charset(JSON_CHARSET))
    }

    private data class HotAreaJson(
        val id: String,
        val centerLat: Double,
        val centerLon: Double,
        val zoom: Double,
        val boundsNorth: Double,
        val boundsSouth: Double,
        val boundsEast: Double,
        val boundsWest: Double,
        val lastVisit: Long,
        val visitCount: Int,
        val totalTimeSec: Long,
        val offlineRegionId: Long?,
    )
}

