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
package org.meshtastic.core.repository

/**
 * The flasher's real-size 3D device models (`/models/devices/<name>.glb`), one per device illustration. A hardware
 * entry's image file names its model by extension swap, and only some illustrations have one.
 */
interface DeviceModelRepository {
    /** Whether a model exists for [imageFile] (an entry of `DeviceHardware.images`). False on any network failure. */
    suspend fun hasModel(imageFile: String): Boolean

    /** The GLB bytes for [imageFile], or null when there is no model or it could not be fetched. */
    suspend fun getModel(imageFile: String): ByteArray?
}
