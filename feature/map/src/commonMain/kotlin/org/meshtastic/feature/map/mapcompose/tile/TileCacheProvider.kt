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

import org.koin.core.annotation.Single

/**
 * App-wide [TileDiskCache] singleton so every map surface shares one LRU budget and one on-disk tree. Resolvable only
 * where the app binds a [TileCacheEnvironment] (desktop today); Koin singles are lazy, so platforms without a binding
 * are unaffected as long as nothing composes the shared map renderer.
 */
@Single
class TileCacheProvider(environment: TileCacheEnvironment) {
    val cache: TileDiskCache = TileDiskCache(environment.fileSystem, environment.cacheRoot)
}
