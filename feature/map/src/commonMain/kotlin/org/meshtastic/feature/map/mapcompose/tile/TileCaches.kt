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
package org.meshtastic.feature.map.mapcompose.tile

/**
 * Process-wide [TileDiskCache] registry so every map surface over the same cache root shares one LRU budget and one
 * in-memory index. Deliberately NOT a Koin definition: `FeatureMapModule` is included on every platform, but only
 * platforms that actually render the shared map bind a [TileCacheEnvironment] — resolving it here at use-time (from
 * composition, main-thread only) keeps the Android Koin graph verifiable without a stub binding.
 */
internal object TileCaches {
    private val caches = mutableMapOf<String, TileDiskCache>()

    fun shared(environment: TileCacheEnvironment): TileDiskCache = caches.getOrPut(environment.cacheRoot.toString()) {
        TileDiskCache(environment.fileSystem, environment.cacheRoot)
    }
}
