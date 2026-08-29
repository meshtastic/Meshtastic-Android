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

import okio.Path

/**
 * Where imported layer files are kept for this platform.
 *
 * Imports are copied here rather than read from wherever the user picked them: the source may be a `content://` the app
 * has no lasting permission for, or a file the user later moves. The copy is what the layer list persists — the
 * directory listing *is* the layer list on restart.
 */
expect fun mapLayersDirectory(): Path

/**
 * A file the user picked, handed to [MapLayersManager] by whatever picker the platform uses.
 *
 * The indirection is what makes the layer store common. Android resolves a `content://` through a ContentResolver and
 * desktop opens a plain path; neither detail belongs in the store, so the caller does the resolving and passes the
 * bytes behind [read].
 */
class PickedMapFile(
    /** The name to show, and the base of the on-disk file name. Untrusted — see [layerFileName]. */
    val displayName: String,
    /** File extension or MIME subtype, resolved to a [LayerType] by [resolveLayerType]. */
    val extensionOrMime: String?,
    /** Reads the picked file. Null if it could not be opened, which is reported and skipped rather than thrown. */
    val read: suspend () -> ByteArray?,
)
