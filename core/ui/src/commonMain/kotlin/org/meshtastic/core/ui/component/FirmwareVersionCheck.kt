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

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.touchlab.kermit.Logger
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceVersion
import org.meshtastic.core.model.FirmwareCheckStatus
import org.meshtastic.core.model.FirmwareGate
import org.meshtastic.core.model.gate
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.disconnect
import org.meshtastic.core.resources.firmware_gate_message
import org.meshtastic.core.resources.firmware_gate_title
import org.meshtastic.core.resources.firmware_gate_update
import org.meshtastic.core.resources.firmware_old
import org.meshtastic.core.resources.firmware_too_old
import org.meshtastic.core.ui.viewmodel.UIViewModel

/**
 * Checks the connected device's firmware version against the app's minimum. Below [DeviceVersion.ABS_MIN_FW_VERSION] a
 * blocking alert disconnects the radio. Below [DeviceVersion.MIN_FW_VERSION] the radio stays connected and a
 * non-dismissable gate offers the in-app firmware update, hidden while [isFirmwareUpdateOpen].
 */
@Composable
fun FirmwareVersionCheck(viewModel: UIViewModel, isFirmwareUpdateOpen: Boolean, onUpdateFirmware: () -> Unit) {
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

    val connected = connectionState == ConnectionState.Connected
    val curVer = myFirmwareVersion?.let { DeviceVersion(it) }
    val gate = if (connected && curVer != null) curVer.checkStatus.gate else FirmwareGate.NONE

    LaunchedEffect(connectionState, myNodeInfo) {
        if (connectionState == ConnectionState.Connected) {
            myFirmwareVersion?.let { fwVersion ->
                val ver = DeviceVersion(fwVersion)
                Logger.i {
                    "[FW_CHECK] Firmware version comparison - " +
                        "device: $ver (raw: $fwVersion), status: ${ver.checkStatus}, " +
                        "absoluteMin: ${DeviceVersion(DeviceVersion.ABS_MIN_FW_VERSION)}, " +
                        "min: ${DeviceVersion(DeviceVersion.MIN_FW_VERSION)}"
                }

                when (ver.checkStatus) {
                    FirmwareCheckStatus.TOO_OLD -> {
                        Logger.w {
                            "[FW_CHECK] Firmware too old - device: $ver < " +
                                "absoluteMin: ${DeviceVersion(DeviceVersion.ABS_MIN_FW_VERSION)}"
                        }
                        val title = getString(Res.string.firmware_too_old)
                        val message = getString(Res.string.firmware_old)
                        viewModel.showAlert(
                            title = title,
                            html = message,
                            onConfirm = { viewModel.setDeviceAddress("n") },
                        )
                    }

                    FirmwareCheckStatus.SHOULD_UPDATE ->
                        Logger.w {
                            "[FW_CHECK] Firmware below min - device: $ver < " +
                                "min: ${DeviceVersion(DeviceVersion.MIN_FW_VERSION)}, gating on update"
                        }

                    // An unparseable/transient version must not disconnect the device (#3726); just log it.
                    FirmwareCheckStatus.UNKNOWN ->
                        Logger.w { "[FW_CHECK] Firmware version unparseable - device raw: $fwVersion, skipping" }

                    FirmwareCheckStatus.OK ->
                        Logger.i { "[FW_CHECK] Firmware version OK - device: $ver meets requirements" }
                }
            }
        }
    }

    if (gate == FirmwareGate.REQUIRE_UPDATE && curVer != null && !isFirmwareUpdateOpen) {
        FirmwareUpdateGate(
            deviceVersion = curVer.asString,
            latestStable = latestStableFirmwareRelease.asString,
            onUpdateFirmware = onUpdateFirmware,
            onDisconnect = { viewModel.setDeviceAddress("n") },
        )
    }
}

@Composable
private fun FirmwareUpdateGate(
    deviceVersion: String,
    latestStable: String,
    onUpdateFirmware: () -> Unit,
    onDisconnect: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text(stringResource(Res.string.firmware_gate_title)) },
        text = {
            Text(
                stringResource(
                    Res.string.firmware_gate_message,
                    deviceVersion,
                    DeviceVersion.MIN_FW_VERSION,
                    latestStable,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onUpdateFirmware) { Text(stringResource(Res.string.firmware_gate_update)) }
        },
        dismissButton = { TextButton(onClick = onDisconnect) { Text(stringResource(Res.string.disconnect)) } },
    )
}
