/*
 * Copyright (c) 2025 Meshtastic LLC
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
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.getString
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.firmware_update_rebooting
import java.io.File
import javax.inject.Inject

private const val REBOOT_DELAY = 5000L

/** Handles firmware updates via USB Mass Storage (UF2). */
class UsbUpdateHandler
@Inject
constructor(
    private val firmwareRetriever: FirmwareRetriever,
    private val serviceRepository: ServiceRepository,
) : FirmwareUpdateHandler {

    override suspend fun startUpdate(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        target: String, // Unused for USB
        updateState: (FirmwareUpdateState) -> Unit,
        firmwareUri: Uri?,
    ): File? =
        try {
            updateState(FirmwareUpdateState.Downloading(0f))

            val rebootingMsg = getString(Res.string.firmware_update_rebooting)

            if (firmwareUri != null) {
                updateState(FirmwareUpdateState.Processing(rebootingMsg))
                serviceRepository.meshService?.rebootToDfu()
                delay(REBOOT_DELAY)

                updateState(FirmwareUpdateState.AwaitingFileSave(null, "firmware.uf2", firmwareUri))
                null
            } else {
                val firmwareFile =
                    firmwareRetriever.retrieveUsbFirmware(release, hardware) { progress ->
                        updateState(FirmwareUpdateState.Downloading(progress))
                    }

                if (firmwareFile == null) {
                    updateState(FirmwareUpdateState.Error("Could not retrieve firmware file."))
                    null
                } else {
                    updateState(FirmwareUpdateState.Processing(rebootingMsg))
                    serviceRepository.meshService?.rebootToDfu()
                    delay(REBOOT_DELAY)

                    updateState(FirmwareUpdateState.AwaitingFileSave(firmwareFile, firmwareFile.name))
                    firmwareFile
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(e) { "USB Update failed" }
            updateState(FirmwareUpdateState.Error(e.message ?: "USB Update failed"))
            null
        }
}
