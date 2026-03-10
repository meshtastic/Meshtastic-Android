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
package org.meshtastic.app.firmware

import org.koin.core.annotation.KoinViewModel
import org.meshtastic.core.data.repository.FirmwareReleaseRepository
import org.meshtastic.core.datastore.BootloaderWarningDataSource
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.repository.DeviceHardwareRepository
import org.meshtastic.core.repository.NodeRepository
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.feature.firmware.FirmwareFileHandler
import org.meshtastic.feature.firmware.FirmwareUpdateManager
import org.meshtastic.feature.firmware.FirmwareUpdateViewModel
import org.meshtastic.feature.firmware.FirmwareUsbManager

@Suppress("LongParameterList")
@KoinViewModel
class AndroidFirmwareUpdateViewModel(
    firmwareReleaseRepository: FirmwareReleaseRepository,
    deviceHardwareRepository: DeviceHardwareRepository,
    nodeRepository: NodeRepository,
    radioController: RadioController,
    radioPrefs: RadioPrefs,
    bootloaderWarningDataSource: BootloaderWarningDataSource,
    firmwareUpdateManager: FirmwareUpdateManager,
    usbManager: FirmwareUsbManager,
    fileHandler: FirmwareFileHandler,
) : FirmwareUpdateViewModel(
    firmwareReleaseRepository,
    deviceHardwareRepository,
    nodeRepository,
    radioController,
    radioPrefs,
    bootloaderWarningDataSource,
    firmwareUpdateManager,
    usbManager,
    fileHandler,
)
