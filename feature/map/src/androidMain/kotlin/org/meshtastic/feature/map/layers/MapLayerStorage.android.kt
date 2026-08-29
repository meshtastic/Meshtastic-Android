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
package org.meshtastic.feature.map.layers

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.meshtastic.core.common.ContextServices
import java.io.File

/** App-internal storage, so imported layers are private to the app and removed with it. */
actual fun mapLayersDirectory(): Path = File(ContextServices.app.filesDir, LAYERS_DIR).absolutePath.toPath()

actual fun mapLayerFileSystem(): FileSystem = FileSystem.SYSTEM

/** The app cache, which Android may clear under pressure — exactly right for a reconvertible copy. */
actual fun mapLayersCacheDirectory(): Path = File(ContextServices.app.cacheDir, "kml-geojson").absolutePath.toPath()
