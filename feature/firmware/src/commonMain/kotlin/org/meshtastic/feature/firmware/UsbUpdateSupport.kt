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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.firmware_update_downloading_percent
import org.meshtastic.core.resources.firmware_update_rebooting
import org.meshtastic.core.resources.firmware_update_retrieval_failed
import org.meshtastic.core.resources.firmware_update_usb_failed
import org.meshtastic.core.resources.getStringSuspend

private const val USB_REBOOT_DELAY = 5000L
private const val PERCENT_MAX = 100

@Suppress("LongMethod")
internal suspend fun performUsbUpdate(
    release: FirmwareRelease,
    hardware: DeviceHardware,
    firmwareUri: CommonUri?,
    radioController: RadioController,
    nodeRepository: NodeRepository,
    updateState: (FirmwareUpdateState) -> Unit,
    retrieveUsbFirmware: suspend (FirmwareRelease, DeviceHardware, (Float) -> Unit) -> FirmwareArtifact?,
): FirmwareArtifact? {
    var cleanupArtifact: FirmwareArtifact? = null
    return try {
        val downloadingMsg =
            getStringSuspend(Res.string.firmware_update_downloading_percent, 0)
                .replace(Regex(":?\\s*%1\\\$d%?"), "")
                .trim()

        updateState(
            FirmwareUpdateState.Downloading(
                ProgressState(message = UiText.DynamicString(downloadingMsg), progress = 0f),
            ),
        )

        if (firmwareUri != null) {
            updateState(
                FirmwareUpdateState.Processing(ProgressState(UiText.Resource(Res.string.firmware_update_rebooting))),
            )
            val myNodeNum = nodeRepository.myNodeInfo.value?.myNodeNum ?: 0
            radioController.rebootToDfu(myNodeNum)
            delay(USB_REBOOT_DELAY)

            val sourceArtifact =
                FirmwareArtifact(uri = firmwareUri, fileName = firmwareUri.pathSegments.lastOrNull() ?: "firmware.uf2")
            updateState(FirmwareUpdateState.AwaitingFileSave(sourceArtifact, sourceArtifact.fileName ?: "firmware.uf2"))
            null
        } else {
            val firmwareFile =
                retrieveUsbFirmware(release, hardware) { progress ->
                    val percent = (progress * PERCENT_MAX).toInt()
                    updateState(
                        FirmwareUpdateState.Downloading(
                            ProgressState(
                                message = UiText.DynamicString(downloadingMsg),
                                progress = progress,
                                details = "$percent%",
                            ),
                        ),
                    )
                }
            cleanupArtifact = firmwareFile

            if (firmwareFile == null) {
                updateState(
                    FirmwareUpdateState.Error(
                        UiText.DynamicString(getStringSuspend(Res.string.firmware_update_retrieval_failed)),
                    ),
                )
                null
            } else {
                val processingState = ProgressState(UiText.Resource(Res.string.firmware_update_rebooting))
                updateState(FirmwareUpdateState.Processing(processingState))
                val myNodeNum = nodeRepository.myNodeInfo.value?.myNodeNum ?: 0
                radioController.rebootToDfu(myNodeNum)
                delay(USB_REBOOT_DELAY)

                val fileName = firmwareFile.fileName ?: "firmware.uf2"
                val fileSaveState = FirmwareUpdateState.AwaitingFileSave(firmwareFile, fileName)
                updateState(fileSaveState)
                firmwareFile
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Logger.e(e) { "USB Update failed" }
        val usbFailedMsg = getStringSuspend(Res.string.firmware_update_usb_failed)
        updateState(FirmwareUpdateState.Error(UiText.DynamicString(e.message ?: usbFailedMsg)))
        cleanupArtifact
    }
}
