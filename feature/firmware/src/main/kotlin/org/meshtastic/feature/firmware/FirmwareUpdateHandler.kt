/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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

import android.net.Uri
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.model.DeviceHardware
import java.io.File

/** Common interface for all firmware update handlers (BLE DFU, ESP32 OTA, USB). */
interface FirmwareUpdateHandler {
    /**
     * Start the firmware update process.
     *
     * @param release The firmware release to install
     * @param hardware The target device hardware
     * @param target The target identifier (e.g., Bluetooth address, IP address, or empty for USB)
     * @param updateState Callback to report back state changes
     * @param firmwareUri Optional URI for a local firmware file (bypasses download)
     * @return The downloaded/extracted firmware file, or null if it was a local file or update finished
     */
    suspend fun startUpdate(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        target: String,
        updateState: (FirmwareUpdateState) -> Unit,
        firmwareUri: Uri? = null,
    ): File?
}
