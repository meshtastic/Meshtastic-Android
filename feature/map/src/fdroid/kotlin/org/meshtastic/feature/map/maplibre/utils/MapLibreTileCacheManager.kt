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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import timber.log.Timber

/**
 * Simplified tile cache manager for MapLibre. Provides basic cache clearing functionality. Note: MapLibre automatically
 * caches tiles via HTTP caching as you view the map.
 */
class MapLibreTileCacheManager(private val context: Context) {
    // Lazy initialization - only create OfflineManager after MapLibre is initialized
    private val offlineManager: OfflineManager by lazy { OfflineManager.getInstance(context) }

    /**
     * Clears the ambient cache (automatic HTTP tile cache). MapLibre automatically caches tiles in the "ambient cache"
     * as you view the map. This method clears that cache to free up storage space.
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        Timber.tag("MapLibreTileCacheManager").d("Clearing ambient cache...")

        offlineManager.clearAmbientCache(
            object : OfflineManager.FileSourceCallback {
                override fun onSuccess() {
                    Timber.tag("MapLibreTileCacheManager").d("Successfully cleared ambient cache")
                }

                override fun onError(message: String) {
                    Timber.tag("MapLibreTileCacheManager").e("Failed to clear ambient cache: $message")
                }
            },
        )

        // Also delete any offline regions if they exist (from the old broken implementation)
        offlineManager.listOfflineRegions(
            object : OfflineManager.ListOfflineRegionsCallback {
                override fun onList(offlineRegions: Array<OfflineRegion>?) {
                    if (offlineRegions == null || offlineRegions.isEmpty()) {
                        Timber.tag("MapLibreTileCacheManager").d("No offline regions to clean up")
                        return
                    }

                    Timber.tag("MapLibreTileCacheManager")
                        .d("Cleaning up ${offlineRegions.size} offline regions from old implementation")
                    offlineRegions.forEach { region ->
                        region.delete(
                            object : OfflineRegion.OfflineRegionDeleteCallback {
                                override fun onDelete() {
                                    Timber.tag("MapLibreTileCacheManager").d("Deleted offline region ${region.id}")
                                }

                                override fun onError(error: String) {
                                    Timber.tag("MapLibreTileCacheManager")
                                        .e("Failed to delete region ${region.id}: $error")
                                }
                            },
                        )
                    }
                }

                override fun onError(error: String) {
                    Timber.tag("MapLibreTileCacheManager").e("Failed to list offline regions: $error")
                }
            },
        )
    }

    /**
     * Sets the maximum size for the ambient cache in bytes. Default is typically 50MB. Call this to increase or
     * decrease the cache size.
     */
    fun setMaximumAmbientCacheSize(sizeBytes: Long, callback: OfflineManager.FileSourceCallback? = null) {
        Timber.tag("MapLibreTileCacheManager").d("Setting maximum ambient cache size to $sizeBytes bytes")
        offlineManager.setMaximumAmbientCacheSize(
            sizeBytes,
            callback
                ?: object : OfflineManager.FileSourceCallback {
                    override fun onSuccess() {
                        Timber.tag("MapLibreTileCacheManager").d("Successfully set maximum ambient cache size")
                    }

                    override fun onError(message: String) {
                        Timber.tag("MapLibreTileCacheManager").e("Failed to set maximum ambient cache size: $message")
                    }
                },
        )
    }
}
