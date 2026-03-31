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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.meshtastic.core.common.util.CommonUri
import org.meshtastic.core.database.entity.FirmwareRelease
import org.meshtastic.core.model.DeviceHardware
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.repository.isBle
import org.meshtastic.core.repository.isSerial
import org.meshtastic.core.repository.isTcp
import org.koin.core.annotation.Single
import org.meshtastic.core.resources.UiText

@Single
class DesktopFirmwareUpdateManager(
    private val radioPrefs: RadioPrefs,
    private val usbUpdateHandler: DesktopUsbUpdateHandler,
) : FirmwareUpdateManager {
    override suspend fun startUpdate(
        release: FirmwareRelease,
        hardware: DeviceHardware,
        address: String,
        updateState: (FirmwareUpdateState) -> Unit,
        firmwareUri: CommonUri?,
    ): FirmwareArtifact? {
        val handler = getHandlerOrNull(hardware, updateState) ?: return null
        return handler.startUpdate(
            release = release,
            hardware = hardware,
            target = getTarget(address),
            updateState = updateState,
            firmwareUri = firmwareUri,
        )
    }

    override fun dfuProgressFlow(): Flow<DfuInternalState> = emptyFlow()

    private fun getHandlerOrNull(
        hardware: DeviceHardware,
        updateState: (FirmwareUpdateState) -> Unit,
    ): FirmwareUpdateHandler? = when {
        radioPrefs.isSerial() -> {
            if (hardware.isEsp32Arc) {
                updateState(
                    FirmwareUpdateState.Error(
                        UiText.DynamicString("Desktop serial firmware update is not supported for ESP32 devices"),
                    ),
                )
                null
            } else {
                usbUpdateHandler
            }
        }

        radioPrefs.isBle() -> {
            updateState(
                FirmwareUpdateState.Error(UiText.DynamicString("Desktop BLE firmware update is not wired yet")),
            )
            null
        }

        radioPrefs.isTcp() -> {
            updateState(FirmwareUpdateState.Error(UiText.DynamicString("Desktop Wi-Fi OTA is not wired yet")))
            null
        }

        else -> {
            updateState(
                FirmwareUpdateState.Error(UiText.DynamicString("Unknown connection type for firmware update")),
            )
            null
        }
    }

    private fun getTarget(address: String): String = if (radioPrefs.isSerial()) "" else address
}
