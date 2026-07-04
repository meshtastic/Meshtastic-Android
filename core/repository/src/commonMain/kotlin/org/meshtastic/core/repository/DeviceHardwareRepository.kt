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

import kotlinx.coroutines.flow.Flow
import org.meshtastic.core.model.DeviceHardware

interface DeviceHardwareRepository {
    /**
     * Retrieves device hardware information by its model ID and optional target string.
     *
     * @param hwModel The hardware model identifier.
     * @param target Optional PlatformIO target environment name to disambiguate multiple variants.
     * @param forceRefresh If true, the local cache will be invalidated and data will be fetched remotely.
     * @return A [Result] containing the [DeviceHardware] on success (or null if not found), or an exception on failure.
     */
    suspend fun getDeviceHardwareByModel(
        hwModel: Int,
        target: String? = null,
        forceRefresh: Boolean = false,
    ): Result<DeviceHardware?>

    /**
     * Observes device hardware information as a non-blocking [Flow].
     *
     * Emits cached/bundled data immediately and refreshes from the network in the background if stale. Suitable for use
     * inside Flow `combine` transforms where suspending would block the UI.
     *
     * @param hwModel The hardware model identifier.
     * @param target Optional PlatformIO target environment name to disambiguate multiple variants.
     */
    fun observeDeviceHardware(hwModel: Int, target: String? = null): Flow<DeviceHardware?>
}
