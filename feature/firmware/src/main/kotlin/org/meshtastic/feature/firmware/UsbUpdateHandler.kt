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
import org.meshtastic.core.strings.firmware_update_downloading_percent
import org.meshtastic.core.strings.firmware_update_rebooting
import org.meshtastic.core.strings.firmware_update_retrieval_failed
import org.meshtastic.core.strings.firmware_update_usb_failed
import java.io.File
import javax.inject.Inject

private const val REBOOT_DELAY = 5000L
private const val PERCENT_MAX = 100

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
            val downloadingMsg =
                getString(Res.string.firmware_update_downloading_percent, 0)
                    .replace(Regex(":?\\s*%1\\\$d%?"), "")
                    .trim()

            updateState(FirmwareUpdateState.Downloading(ProgressState(message = downloadingMsg, progress = 0f)))

            val rebootingMsg = getString(Res.string.firmware_update_rebooting)

            if (firmwareUri != null) {
                updateState(FirmwareUpdateState.Processing(ProgressState(rebootingMsg)))
                serviceRepository.meshService?.rebootToDfu()
                delay(REBOOT_DELAY)

                updateState(FirmwareUpdateState.AwaitingFileSave(null, "firmware.uf2", firmwareUri))
                null
            } else {
                val firmwareFile =
                    firmwareRetriever.retrieveUsbFirmware(release, hardware) { progress ->
                        val percent = (progress * PERCENT_MAX).toInt()
                        updateState(
                            FirmwareUpdateState.Downloading(
                                ProgressState(message = downloadingMsg, progress = progress, details = "$percent%"),
                            ),
                        )
                    }

                if (firmwareFile == null) {
                    val retrievalFailedMsg = getString(Res.string.firmware_update_retrieval_failed)
                    updateState(FirmwareUpdateState.Error(retrievalFailedMsg))
                    null
                } else {
                    updateState(FirmwareUpdateState.Processing(ProgressState(rebootingMsg)))
                    serviceRepository.meshService?.rebootToDfu()
                    delay(REBOOT_DELAY)

                    updateState(FirmwareUpdateState.AwaitingFileSave(firmwareFile, firmwareFile.name))
                    firmwareFile
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Logger.e(e) { "USB Update failed" }
            val usbFailedMsg = getString(Res.string.firmware_update_usb_failed)
            updateState(FirmwareUpdateState.Error(e.message ?: usbFailedMsg))
            null
        }
}
