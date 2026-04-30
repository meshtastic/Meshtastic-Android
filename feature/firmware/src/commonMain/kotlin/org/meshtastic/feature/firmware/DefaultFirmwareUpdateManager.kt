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

import org.koin.core.annotation.Single
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.repository.isBle
import org.meshtastic.core.repository.isSerial
import org.meshtastic.core.repository.isTcp
import org.meshtastic.feature.firmware.ota.Esp32OtaUpdateHandler
import org.meshtastic.feature.firmware.ota.dfu.SecureDfuHandler

/**
 * Default [FirmwareUpdateManager] that routes to the correct handler based on the current connection type and device
 * architecture. All handlers are KMP-ready and work on Android, Desktop, and (future) iOS.
 */
@Single
class DefaultFirmwareUpdateManager(
    private val radioPrefs: RadioPrefs,
    private val secureDfuHandler: SecureDfuHandler,
    private val usbUpdateHandler: UsbUpdateHandler,
    private val esp32OtaUpdateHandler: Esp32OtaUpdateHandler,
) : FirmwareUpdateManager {

    override suspend fun startUpdate(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        address: String,
        updateState: (FirmwareUpdateState) -> Unit,
        firmwareUri: CommonUri?,
    ): FirmwareArtifact? {
        val handler = getHandler(hardware)
        val target = getTarget(address)

        return handler.startUpdate(
            release = release,
            hardware = hardware,
            target = target,
            updateState = updateState,
            firmwareUri = firmwareUri,
        )
    }

    internal fun getHandler(hardware: DeviceHardware): FirmwareUpdateHandler = when {
        radioPrefs.isSerial() -> {
            if (hardware.isEsp32Arc) {
                error("Serial/USB firmware update not supported for ESP32 devices")
            }
            usbUpdateHandler
        }

        radioPrefs.isBle() -> {
            if (hardware.isEsp32Arc) {
                esp32OtaUpdateHandler
            } else {
                secureDfuHandler
            }
        }

        radioPrefs.isTcp() -> {
            if (hardware.isEsp32Arc) {
                esp32OtaUpdateHandler
            } else {
                error("WiFi OTA only supported for ESP32 devices")
            }
        }

        else -> error("Unknown connection type for firmware update")
    }

    internal fun getTarget(address: String): String = when {
        radioPrefs.isSerial() -> ""
        radioPrefs.isBle() -> address
        radioPrefs.isTcp() -> address
        else -> ""
    }
}
