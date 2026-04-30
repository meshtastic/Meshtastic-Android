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
package org.meshtastic.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.getString
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceVersion
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.firmware_old
import org.meshtastic.core.resources.firmware_too_old
import org.meshtastic.core.resources.should_update
import org.meshtastic.core.resources.should_update_firmware
import org.meshtastic.core.ui.viewmodel.UIViewModel

/**
 * Common component to check the connected device's firmware version against the minimum required version. Will display
 * a dismissable alert if the firmware is old, or a blocking alert if it is too old.
 */
@Composable
fun FirmwareVersionCheck(viewModel: UIViewModel) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val myNodeInfo by viewModel.myNodeInfo.collectAsStateWithLifecycle()

    val myFirmwareVersion = myNodeInfo?.firmwareVersion

    val firmwareEdition by viewModel.firmwareEdition.collectAsStateWithLifecycle(null)

    val latestStableFirmwareRelease by
        viewModel.latestStableFirmwareRelease.collectAsStateWithLifecycle(DeviceVersion("2.6.4"))

    LaunchedEffect(connectionState, firmwareEdition) {
        if (connectionState == ConnectionState.Connected) {
            firmwareEdition?.let { edition -> Logger.d { "FirmwareEdition: ${edition.name}" } }
        }
    }

    LaunchedEffect(connectionState, myNodeInfo) {
        if (connectionState == ConnectionState.Connected) {
            myNodeInfo?.let { info ->
                myFirmwareVersion
                    ?.takeIf { it.isNotBlank() }
                    ?.let { fwVersion ->
                        val curVer = DeviceVersion(fwVersion)
                        Logger.i {
                            "[FW_CHECK] Firmware version comparison - " +
                                "device: $curVer (raw: $fwVersion), " +
                                "absoluteMin: ${DeviceVersion(DeviceVersion.ABS_MIN_FW_VERSION)}, " +
                                "min: ${DeviceVersion(DeviceVersion.MIN_FW_VERSION)}"
                        }

                        if (curVer < DeviceVersion(DeviceVersion.ABS_MIN_FW_VERSION)) {
                            Logger.w {
                                "[FW_CHECK] Firmware too old - " +
                                    "device: $curVer < absoluteMin: ${DeviceVersion(DeviceVersion.ABS_MIN_FW_VERSION)}"
                            }
                            val title = getString(Res.string.firmware_too_old)
                            val message = getString(Res.string.firmware_old)
                            viewModel.showAlert(
                                title = title,
                                html = message,
                                onConfirm = { viewModel.setDeviceAddress("n") },
                            )
                        } else if (curVer < DeviceVersion(DeviceVersion.MIN_FW_VERSION)) {
                            Logger.w {
                                "[FW_CHECK] Firmware should update - " +
                                    "device: $curVer < min: ${DeviceVersion(DeviceVersion.MIN_FW_VERSION)}"
                            }
                            val title = getString(Res.string.should_update_firmware)
                            val message = getString(Res.string.should_update, latestStableFirmwareRelease.asString)
                            viewModel.showAlert(title = title, message = message, onConfirm = {})
                        } else {
                            Logger.i { "[FW_CHECK] Firmware version OK - device: $curVer meets requirements" }
                        }
                    }
            }
        }
    }
}
