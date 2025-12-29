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
import co.touchlab.kermit.Logger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import no.nordicsemi.android.dfu.DfuServiceInitiator
import org.jetbrains.compose.resources.getString
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.service.ServiceRepository
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.firmware_update_not_found_in_release
import org.meshtastic.core.strings.firmware_update_starting_service
import java.io.File
import javax.inject.Inject

private const val SCAN_TIMEOUT = 2000L
private const val PACKETS_BEFORE_PRN = 8
private const val DATA_OBJECT_DELAY = 400L

/** Handles Over-the-Air (OTA) firmware updates for nRF52-based devices using the Nordic DFU library. */
class NordicDfuHandler
@Inject
constructor(
    private val firmwareRetriever: FirmwareRetriever,
    @ApplicationContext private val context: Context,
    private val serviceRepository: ServiceRepository,
) : FirmwareUpdateHandler {

    override suspend fun startUpdate(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        target: String, // Bluetooth address
        updateState: (FirmwareUpdateState) -> Unit,
        firmwareUri: Uri?,
    ): File? =
        try {
            updateState(FirmwareUpdateState.Downloading(0f))

            if (firmwareUri != null) {
                initiateDfu(target, hardware, firmwareUri, updateState)
                null
            } else {
                val firmwareFile =
                    firmwareRetriever.retrieveOtaFirmware(release, hardware) { progress ->
                        updateState(FirmwareUpdateState.Downloading(progress))
                    }

                if (firmwareFile == null) {
                    val errorMsg = getString(Res.string.firmware_update_not_found_in_release, hardware.displayName)
                    updateState(FirmwareUpdateState.Error(errorMsg))
                    null
                } else {
                    initiateDfu(target, hardware, Uri.fromFile(firmwareFile), updateState)
                    firmwareFile
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(e) { "Nordic DFU Update failed" }
            updateState(FirmwareUpdateState.Error(e.message ?: "Nordic DFU Update failed"))
            null
        }

    private suspend fun initiateDfu(
        address: String,
        deviceHardware: DeviceHardware,
        firmwareUri: Uri,
        updateState: (FirmwareUpdateState) -> Unit,
    ) {
        val startingMsg = getString(Res.string.firmware_update_starting_service)
        updateState(FirmwareUpdateState.Processing(startingMsg))

        // n = Nordic (Legacy prefix handling in mesh service)
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
