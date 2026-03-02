/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.domain.usecase.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import org.meshtastic.core.data.repository.DeviceHardwareRepository
import org.meshtastic.core.data.repository.NodeRepository
import org.meshtastic.core.database.model.Node
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.RadioController
import org.meshtastic.core.prefs.radio.RadioPrefs
import org.meshtastic.core.prefs.radio.isBle
import org.meshtastic.core.prefs.radio.isSerial
import org.meshtastic.core.prefs.radio.isTcp
import javax.inject.Inject

/**
 * Use case to determine if the currently connected device is capable of over-the-air (OTA) updates.
 */
class IsOtaCapableUseCase @Inject constructor(
    private val nodeRepository: NodeRepository,
    private val radioController: RadioController,
    private val radioPrefs: RadioPrefs,
    private val deviceHardwareRepository: DeviceHardwareRepository,
) {
    operator fun invoke(): Flow<Boolean> =
        combine(nodeRepository.ourNodeInfo, radioController.connectionState) { node: Node?, connectionState: ConnectionState ->
            node to connectionState
        }.flatMapLatest { (node, connectionState) ->
            if (node == null || connectionState != ConnectionState.Connected) {
                flowOf(false)
            } else if (radioPrefs.isBle() || radioPrefs.isSerial() || radioPrefs.isTcp()) {
                val hwModel = node.user.hw_model.value
                val hw = deviceHardwareRepository.getDeviceHardwareByModel(hwModel).getOrNull()
                
                // ESP32 Unified OTA is only supported via BLE or WiFi (TCP), not USB Serial.
                // TODO: Re-enable when supportsUnifiedOta is added to DeviceHardware
                val isEsp32OtaSupported = false
                
                flowOf(hw?.requiresDfu == true || isEsp32OtaSupported)
            } else {
                flowOf(false)
            }
        }
}
