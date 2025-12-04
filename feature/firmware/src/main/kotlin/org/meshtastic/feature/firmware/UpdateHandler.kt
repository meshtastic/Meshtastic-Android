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

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import no.nordicsemi.android.dfu.DfuServiceInitiator
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.service.ServiceRepository
import timber.log.Timber
import java.io.File
import javax.inject.Inject

private const val SCAN_TIMEOUT = 2000L
private const val PACKETS_BEFORE_PRN = 8
private const val REBOOT_DELAY = 5000L

private const val DATA_OBJECT_DELAY = 400L

/** Retrieves firmware files, either by direct download or by extracting from a release asset. */
class FirmwareRetriever @Inject constructor(private val fileHandler: FirmwareFileHandler) {
    suspend fun retrieveOtaFirmware(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        onProgress: (Float) -> Unit,
    ): File? = retrieve(
        release = release,
        hardware = hardware,
        onProgress = onProgress,
        fileSuffix = "-ota.zip",
        internalFileExtension = ".zip",
    )

    suspend fun retrieveUsbFirmware(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        onProgress: (Float) -> Unit,
    ): File? = retrieve(
        release = release,
        hardware = hardware,
        onProgress = onProgress,
        fileSuffix = ".uf2",
        internalFileExtension = ".uf2",
    )

    private suspend fun retrieve(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        onProgress: (Float) -> Unit,
        fileSuffix: String,
        internalFileExtension: String,
    ): File? {
        val version = release.id.removePrefix("v")
        val target = hardware.platformioTarget.ifEmpty { hardware.hwModelSlug }
        val filename = "firmware-$target-$version$fileSuffix"
        val directUrl =
            "https://raw.githubusercontent.com/meshtastic/meshtastic.github.io/master/firmware-$version/$filename"

        if (fileHandler.checkUrlExists(directUrl)) {
            try {
                fileHandler.downloadFile(directUrl, filename, onProgress)?.let {
                    return it
                }
            } catch (e: Exception) {
                Timber.w(e, "Direct download for $filename failed, falling back to release zip")
            }
        }

        // Fallback to downloading the full release zip and extracting
        val zipUrl = getDeviceFirmwareUrl(release.zipUrl, hardware.architecture)
        val downloadedZip = fileHandler.downloadFile(zipUrl, "firmware_release.zip", onProgress)
        return downloadedZip?.let { fileHandler.extractFirmware(it, hardware, internalFileExtension) }
    }

    private fun getDeviceFirmwareUrl(url: String, targetArch: String): String {
        val knownArchs = listOf("esp32-s3", "esp32-c3", "esp32-c6", "nrf52840", "rp2040", "stm32", "esp32")
        for (arch in knownArchs) {
            if (url.contains(arch, ignoreCase = true)) {
                return url.replace(arch, targetArch.lowercase(), ignoreCase = true)
            }
        }
        return url
    }
}

/** Handles the logic for Over-the-Air (OTA) firmware updates via Bluetooth. */
class OtaUpdateHandler
@Inject
constructor(
    private val firmwareRetriever: FirmwareRetriever,
    @ApplicationContext private val context: Context,
    private val serviceRepository: ServiceRepository,
) {
    suspend fun startUpdate(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        address: String,
        updateState: (FirmwareUpdateState) -> Unit,
        notFoundMsg: String,
        startingMsg: String,
        firmwareUri: Uri? = null,
    ): File? = try {
        updateState(FirmwareUpdateState.Downloading(0f))

        if (firmwareUri != null) {
            initiateDfu(address, hardware, firmwareUri, updateState, startingMsg)
            null
        } else {
            val firmwareFile =
                firmwareRetriever.retrieveOtaFirmware(release, hardware) { progress ->
                    updateState(FirmwareUpdateState.Downloading(progress))
                }

            if (firmwareFile == null) {
                updateState(FirmwareUpdateState.Error(notFoundMsg))
                null
            } else {
                initiateDfu(address, hardware, Uri.fromFile(firmwareFile), updateState, startingMsg)
                firmwareFile
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.e(e)
        updateState(FirmwareUpdateState.Error(e.message ?: "OTA Update failed"))
        null
    }

    private fun initiateDfu(
        address: String,
        deviceHardware: DeviceHardware,
        firmwareUri: Uri,
        updateState: (FirmwareUpdateState) -> Unit,
        startingMsg: String,
    ) {
        updateState(FirmwareUpdateState.Processing(startingMsg))
        serviceRepository.meshService?.setDeviceAddress("n")

        DfuServiceInitiator(address)
            .disableResume()
            .setDeviceName(deviceHardware.displayName)
            .setForceScanningForNewAddressInLegacyDfu(true)
            .setForeground(true)
            .setKeepBond(true)
            .setForceDfu(false)
            .setPrepareDataObjectDelay(DATA_OBJECT_DELAY)
            .setPacketsReceiptNotificationsValue(PACKETS_BEFORE_PRN)
            .setScanTimeout(SCAN_TIMEOUT)
            .setUnsafeExperimentalButtonlessServiceInSecureDfuEnabled(true)
            .setZip(firmwareUri)
            .start(context, FirmwareDfuService::class.java)
    }
}

/** Handles the logic for firmware updates via USB. */
class UsbUpdateHandler
@Inject
constructor(
    private val firmwareRetriever: FirmwareRetriever,
    private val serviceRepository: ServiceRepository,
) {
    suspend fun startUpdate(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        updateState: (FirmwareUpdateState) -> Unit,
        rebootingMsg: String,
        firmwareUri: Uri? = null,
    ): File? = try {
        updateState(FirmwareUpdateState.Downloading(0f))

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
        Timber.e(e)
        updateState(FirmwareUpdateState.Error(e.message ?: "USB Update failed"))
        null
    }
}
