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
import kotlinx.coroutines.flow.Flow
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.prefs.radio.RadioPrefs
import org.meshtastic.core.prefs.radio.isBle
import org.meshtastic.core.prefs.radio.isSerial
import org.meshtastic.core.prefs.radio.isTcp
import org.meshtastic.feature.firmware.ota.Esp32OtaUpdateHandler
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Orchestrates the firmware update process by choosing the correct handler. */
@Singleton
class FirmwareUpdateManager
@Inject
constructor(
    private val radioPrefs: RadioPrefs,
    private val nordicDfuHandler: NordicDfuHandler,
    private val usbUpdateHandler: UsbUpdateHandler,
    private val esp32OtaUpdateHandler: Esp32OtaUpdateHandler,
) {

    /** Start the update process based on the current connection and hardware. */
    suspend fun startUpdate(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        address: String,
        updateState: (FirmwareUpdateState) -> Unit,
        firmwareUri: Uri? = null,
    ): File? {
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

    fun dfuProgressFlow(): Flow<DfuInternalState> = nordicDfuHandler.progressFlow()

    private fun getHandler(hardware: DeviceHardware): FirmwareUpdateHandler = when {
        radioPrefs.isSerial() -> usbUpdateHandler
        radioPrefs.isBle() -> {
            if (isEsp32Architecture(hardware.architecture)) {
                esp32OtaUpdateHandler
            } else {
                nordicDfuHandler
            }
        }
        radioPrefs.isTcp() -> {
            if (isEsp32Architecture(hardware.architecture)) {
                esp32OtaUpdateHandler
            } else {
                // Should be handled/validated before calling startUpdate
                error("WiFi OTA only supported for ESP32 devices")
            }
        }
        else -> error("Unknown connection type for firmware update")
    }

    private fun getTarget(address: String): String = when {
        radioPrefs.isSerial() -> ""
        radioPrefs.isBle() -> address
        radioPrefs.isTcp() -> extractIpFromAddress(radioPrefs.devAddr) ?: ""
        else -> ""
    }

    private fun isEsp32Architecture(architecture: String): Boolean = architecture.startsWith("esp32", ignoreCase = true)

    private fun extractIpFromAddress(address: String?): String? =
        if (address != null && address.startsWith("t") && address.length > 1) {
            address.substring(1)
        } else {
            null
        }
}
