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

import org.koin.core.annotation.Single
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.network.SerialTransport
import org.meshtastic.core.network.radio.BaseRadioTransportFactory
import org.meshtastic.core.network.radio.TCPInterface
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.repository.RadioTransportFactory

/**
 * Desktop implementation of [RadioTransportFactory] delegating multiplatform transports (BLE, TCP)
 * and providing platform-specific transports (USB/Serial) via jSerialComm.
 */
@Single(binds = [RadioTransportFactory::class])
class DesktopRadioTransportFactory(
    scanner: BleScanner,
    bluetoothRepository: BluetoothRepository,
    connectionFactory: BleConnectionFactory,
    dispatchers: CoroutineDispatchers,
) : BaseRadioTransportFactory(scanner, bluetoothRepository, connectionFactory, dispatchers) {

    override val supportedDeviceTypes: List<DeviceType> = listOf(DeviceType.TCP, DeviceType.BLE, DeviceType.USB)

    override fun isMockInterface(): Boolean = false

    override fun createPlatformTransport(address: String, service: RadioInterfaceService): RadioTransport =
        when {
            address.startsWith(InterfaceId.TCP.id) -> {
                TCPInterface(service, dispatchers, address.removePrefix(InterfaceId.TCP.id.toString()))
            }
            address.startsWith(InterfaceId.SERIAL.id) -> {
                SerialTransport(portName = address.removePrefix(InterfaceId.SERIAL.id.toString()), service = service)
            }
            else -> error("Unsupported transport for address: $address")
        }
}
