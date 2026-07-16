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

import okio.FileSystem
import okio.Path

/**
 * Where the tile disk cache lives. Bound in each app's Koin module (desktop today; Android when fdroid adopts the
 * shared renderer) rather than in `FeatureMapModule`, because the cache directory is inherently app-specific.
 */
interface TileCacheEnvironment {
    val fileSystem: FileSystem
    val cacheRoot: Path
}
