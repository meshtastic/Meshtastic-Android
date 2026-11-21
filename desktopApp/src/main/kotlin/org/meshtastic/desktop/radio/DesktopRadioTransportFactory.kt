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
package org.meshtastic.desktop.radio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.network.SerialTransport
import org.meshtastic.core.network.radio.NonWebRadioTransportFactory
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.repository.RadioTransportFactory
import org.meshtastic.desktop.DesktopBuildConfig

/**
 * Desktop implementation of [RadioTransportFactory] delegating multiplatform transports (BLE, TCP) and providing
 * platform-specific transports (USB/Serial) via jSerialComm.
 *
 * Registered manually in [DesktopRuntimeModule] — do NOT add @Single to avoid double-registration with
 * the @ComponentScan("org.meshtastic.desktop") in DesktopDiModule.
 */
class DesktopRadioTransportFactory(
    scanner: BleScanner,
    bluetoothRepository: BluetoothRepository,
    connectionFactory: BleConnectionFactory,
    dispatchers: CoroutineDispatchers,
) : NonWebRadioTransportFactory(scanner, bluetoothRepository, connectionFactory, dispatchers) {

    override val supportedDeviceTypes: List<DeviceType> = listOf(DeviceType.TCP, DeviceType.BLE, DeviceType.USB)

    // Desktop has no unlock gesture, so Demo Mode is a debug-build feature: it admits the `m` addresses, including
    // the hidden showcase, and offers the demo entry in the picker.
    override val mockTransportEnabled: StateFlow<Boolean> = MutableStateFlow(DesktopBuildConfig.IS_DEBUG)

    /**
     * Desktop bundles no capture asset, so an `r` address opens the plain mock and the picker offers no replay entry.
     */
    override val isReplayTransportAvailable: Boolean = false

    override fun createSerialTransport(service: RadioInterfaceService, rest: String): RadioTransport =
        SerialTransport.create(
            portName = rest,
            callback = service,
            scope = service.serviceScope,
            dispatchers = dispatchers,
        )
}
