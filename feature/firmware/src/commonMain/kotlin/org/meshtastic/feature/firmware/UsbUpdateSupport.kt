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
import org.meshtastic.core.common.util.safeCatching
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.UiText
import org.meshtastic.core.resources.firmware_update_copying
import org.meshtastic.core.resources.firmware_update_downloading_percent
import org.meshtastic.core.resources.firmware_update_flashing
import org.meshtastic.core.resources.firmware_update_rebooting
import org.meshtastic.core.resources.firmware_update_retrieval_failed
import org.meshtastic.core.resources.firmware_update_usb_failed
import org.meshtastic.core.resources.getStringSuspend

private const val USB_REBOOT_DELAY = 5000L
private const val PERCENT_MAX = 100

/**
 * One leg of a multi-pass USB/UF2 sequence.
 *
 * Two shapes because the passes differ in when their image becomes knowable: the application image is downloaded and
 * verified up front, while an erase or bootloader image is chosen from what the mounted volume reports about itself, so
 * it cannot exist until the user has pointed at the drive.
 */
internal sealed interface UsbFileSavePass {
    val step: UsbFileSaveStep

    /** Image identity is decided from the mounted volume; nothing has been downloaded yet. */
    data class FromVolume(
        override val step: UsbFileSaveStep,
        val request: UsbMaintenanceRequest,
        /**
         * True for the nRF factory-erase image, which blocks on `while (!Serial)` before formatting and needs a host to
         * assert DTR before it will run.
         */
        val requiresCdcUnblock: Boolean,
    ) : UsbFileSavePass

    /** Already downloaded and verified. */
    data class Prepared(override val step: UsbFileSaveStep, val artifact: FirmwareArtifact, val fileName: String) :
        UsbFileSavePass
}

/**
 * Prepares a USB maintenance sequence: fetch the application image, reboot into the bootloader, and publish the first
 * pass.
 *
 * Ordering is deliberate. The **application** image is downloaded and verified *before* `rebootToDfu`, because it is
 * what restores the device after a destructive write — we never erase without already holding the firmware to put back.
 * The **maintenance** image cannot be fetched this early: erase and bootloader images are selected from the Board-ID
 * and SoftDevice the mounted volume reports, and no volume exists until the device has rebooted. It is fetched later,
 * after the volume is vetted but still before anything is written, so a failure there costs a message rather than a
 * device.
 *
 * @return The ordered passes, or an empty list when preparation failed (state has already been set to an error).
 */
internal suspend fun performUsbMaintenance(
    request: UsbMaintenanceRequest,
    release: FirmwareRelease,
    hardware: DeviceHardware,
    radioController: RadioController,
    nodeRepository: NodeRepository,
    updateState: (FirmwareUpdateState) -> Unit,
    retrieveUsbFirmware: suspend (FirmwareRelease, DeviceHardware, (Float) -> Unit) -> FirmwareArtifact?,
): List<UsbFileSavePass> {
    val downloadingMsg = getStringSuspend(Res.string.firmware_update_downloading_percent, 0).stripFormatArgs()
    updateState(
        FirmwareUpdateState.Downloading(ProgressState(message = UiText.DynamicString(downloadingMsg), progress = 0f)),
    )

    val firmware =
        try {
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
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            Logger.e(e) { "Maintenance firmware download failed" }
            null
        }

    if (firmware == null) {
        updateState(
            FirmwareUpdateState.Error(
                UiText.DynamicString(getStringSuspend(Res.string.firmware_update_retrieval_failed)),
            ),
        )
        return emptyList()
    }

    val maintenanceStep =
        when (request) {
            UsbMaintenanceRequest.FactoryErase -> UsbFileSaveStep.FactoryErase
            UsbMaintenanceRequest.BootloaderUpgrade -> UsbFileSaveStep.BootloaderUpgrade
        }

    updateState(FirmwareUpdateState.Processing(ProgressState(UiText.Resource(Res.string.firmware_update_rebooting))))
    radioController.rebootToDfu(nodeRepository.myNodeInfo.value?.myNodeNum ?: 0)
    delay(USB_REBOOT_DELAY)

    val passes =
        listOf(
            UsbFileSavePass.FromVolume(
                step = maintenanceStep,
                request = request,
                // Only the nRF erase image blocks waiting for a CDC host; pico_erase runs on its own, and a bootloader
                // self-update is consumed by the bootloader itself.
                requiresCdcUnblock = request == UsbMaintenanceRequest.FactoryErase && hardware.isNrf52Arc,
            ),
            UsbFileSavePass.Prepared(
                step = UsbFileSaveStep.Firmware,
                artifact = firmware,
                fileName = firmware.fileName ?: "firmware.uf2",
            ),
        )

    updateState(passes.first().toAwaitingFileSave())
    return passes
}

/** Outcome of attempting one pass. Every failure is typed so the caller can re-offer the same pass with copy. */
internal sealed interface UsbPassResult {
    data object Written : UsbPassResult

    /** Volume vetting or image selection said no. Nothing was written. */
    data class Refused(val reason: UsbMaintenanceRefusal) : UsbPassResult

    data object ImageDownloadFailed : UsbPassResult

    data object CopyFailed : UsbPassResult

    /** The image was copied but the destination is still readable, so it did not land on a bootloader volume. */
    data object WriteDidNotLand : UsbPassResult

    /** The erase image was written but could not be unblocked, so it never ran. Nothing was destroyed. */
    data object CdcUnblockFailed : UsbPassResult
}

private const val DETACH_TIMEOUT_MS = 60_000L
private const val POST_WRITE_SETTLE_MS = 1_500L
private const val CDC_UNBLOCK_WAIT_MS = 15_000L
private const val CDC_DTR_HOLD_MS = 2_000L

/**
 * Writes one leg of a USB/UF2 sequence to a user-picked volume.
 *
 * Dependencies are constructor-injected rather than threaded through the call so the write itself keeps a short
 * signature, and [unblockCdc] is a lambda so the serial plumbing can land separately from this decision logic.
 *
 * @property unblockCdc Opens the erase firmware's CDC port with DTR asserted so it stops waiting and formats. Returns
 *   false when no port could be claimed.
 */
internal class UsbPassWriter(
    private val fileHandler: FirmwareFileHandler,
    private val retrieveMaintenanceUf2: suspend (MaintenanceUf2, (Float) -> Unit) -> FirmwareArtifact?,
    private val awaitDeviceDetach: suspend (Long) -> Boolean,
    private val unblockCdc: suspend (waitMillis: Long, holdMillis: Long) -> Boolean,
) {

    /**
     * Vets [treeUri], resolves the image if this pass needs it, writes, and confirms the write landed.
     *
     * Nothing is written until the volume has proved it is a UF2 bootloader drive and an image has been chosen and
     * verified, so any refusal reaches the user with the device untouched.
     */
    @Suppress("ReturnCount")
    suspend fun write(
        pass: UsbFileSavePass,
        treeUri: CommonUri,
        hardware: DeviceHardware,
        updateState: (FirmwareUpdateState) -> Unit,
    ): UsbPassResult {
        val inspection = inspectMaintenanceVolume(treeUri, fileHandler)
        val volume =
            when (inspection) {
                is VolumeInspection.Rejected -> return UsbPassResult.Refused(inspection.reason)
                is VolumeInspection.Accepted -> inspection.volume
            }

        val artifact =
            when (val resolved = resolveImage(pass, hardware, volume, updateState)) {
                is ImageResolution.Failed -> return resolved.result
                is ImageResolution.Ready -> resolved.artifact
            }

        val fileName = artifact.fileName ?: return UsbPassResult.CopyFailed
        updateState(FirmwareUpdateState.Processing(ProgressState(UiText.Resource(Res.string.firmware_update_copying))))

        val destination = fileHandler.createDocumentInTree(treeUri, fileName, UF2_MIME_TYPE)
        if (destination == null) {
            Logger.w { "Could not create $fileName on the picked volume" }
            return UsbPassResult.CopyFailed
        }

        val copied =
            safeCatching { fileHandler.copyToUri(artifact, destination) }
                .onFailure { Logger.e(it) { "Copying $fileName to the UF2 volume failed" } }
                .getOrNull()
        if (copied == null) return UsbPassResult.CopyFailed

        updateState(FirmwareUpdateState.Processing(ProgressState(UiText.Resource(Res.string.firmware_update_flashing))))

        // A UF2 bootloader consumes the image and reboots, so the volume vanishing is the success signal. Missing the
        // broadcast is common and harmless — the read-back below is the authority either way.
        val detached = awaitDeviceDetach(DETACH_TIMEOUT_MS)
        if (!detached) {
            Logger.d { "No detach observed for $fileName; falling back to the read-back check" }
            delay(POST_WRITE_SETTLE_MS)
            if (fileHandler.isDestinationReadable(destination)) {
                // Still readable means the bytes are sitting somewhere persistent, i.e. this was never a UF2 volume.
                Logger.w { "$fileName is still readable after writing — it did not land on a bootloader volume" }
                return UsbPassResult.WriteDidNotLand
            }
        }

        if (pass is UsbFileSavePass.FromVolume && pass.requiresCdcUnblock) {
            if (!unblockCdc(CDC_UNBLOCK_WAIT_MS, CDC_DTR_HOLD_MS)) {
                // The erase image blocks before InternalFS.format(), so a failed unblock has destroyed nothing.
                return UsbPassResult.CdcUnblockFailed
            }
        }

        return UsbPassResult.Written
    }

    /** Either the image to write, or the result to return instead. */
    private sealed interface ImageResolution {
        data class Ready(val artifact: FirmwareArtifact) : ImageResolution

        data class Failed(val result: UsbPassResult) : ImageResolution
    }

    /**
     * Produces the image for [pass]: already downloaded for the application pass, or chosen from what [volume] reports
     * and fetched now for a maintenance pass.
     */
    private suspend fun resolveImage(
        pass: UsbFileSavePass,
        hardware: DeviceHardware,
        volume: MaintenanceVolume,
        updateState: (FirmwareUpdateState) -> Unit,
    ): ImageResolution = when (pass) {
        is UsbFileSavePass.Prepared -> ImageResolution.Ready(pass.artifact)

        is UsbFileSavePass.FromVolume ->
            when (val choice = chooseMaintenanceImage(pass.request, hardware, volume)) {
                is MaintenanceImageChoice.Refused -> ImageResolution.Failed(UsbPassResult.Refused(choice.reason))

                is MaintenanceImageChoice.Resolved -> {
                    val downloadingMsg =
                        getStringSuspend(Res.string.firmware_update_downloading_percent, 0).stripFormatArgs()
                    val artifact =
                        retrieveMaintenanceUf2(choice.asset) { progress ->
                            updateState(
                                FirmwareUpdateState.Downloading(
                                    ProgressState(
                                        message = UiText.DynamicString(downloadingMsg),
                                        progress = progress,
                                    ),
                                ),
                            )
                        }
                    artifact?.let { ImageResolution.Ready(it) }
                        ?: ImageResolution.Failed(UsbPassResult.ImageDownloadFailed)
                }
            }
    }
}

/** UF2 images are opaque binaries as far as the document provider is concerned. */
internal const val UF2_MIME_TYPE = "application/octet-stream"

/** The state that asks the user to point at the drive for this pass. */
internal fun UsbFileSavePass.toAwaitingFileSave(retryMessage: UiText? = null): FirmwareUpdateState.AwaitingFileSave =
    when (this) {
        is UsbFileSavePass.FromVolume ->
            FirmwareUpdateState.AwaitingFileSave(
                uf2Artifact = null,
                fileName = null,
                step = step,
                retryMessage = retryMessage,
            )

        is UsbFileSavePass.Prepared ->
            FirmwareUpdateState.AwaitingFileSave(
                uf2Artifact = artifact,
                fileName = fileName,
                step = step,
                retryMessage = retryMessage,
            )
    }

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
        val downloadingMsg = getStringSuspend(Res.string.firmware_update_downloading_percent, 0).stripFormatArgs()

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
