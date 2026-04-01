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
package org.meshtastic.feature.firmware

import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.model.DeviceHardware

/**
 * Routes firmware update requests to the appropriate platform-specific handler based on the active connection type
 * (BLE, WiFi/TCP, or USB) and device architecture.
 */
interface FirmwareUpdateManager {
    /**
     * Begin a firmware update for the connected device.
     *
     * @param release The firmware release to install.
     * @param hardware The target device's hardware descriptor.
     * @param address The bare device address (MAC, IP, or serial path) with the transport prefix stripped.
     * @param updateState Callback invoked as the update progresses through [FirmwareUpdateState] stages.
     * @param firmwareUri Optional pre-selected firmware file URI (for "update from file" flows).
     * @return A [FirmwareArtifact] that should be cleaned up by the caller, or `null` if the update was not started.
     */
    suspend fun startUpdate(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        address: String,
        updateState: (FirmwareUpdateState) -> Unit,
        firmwareUri: CommonUri? = null,
    ): FirmwareArtifact?
}
