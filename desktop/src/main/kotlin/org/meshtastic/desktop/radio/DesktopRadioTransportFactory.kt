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

import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.network.SerialTransport
import org.meshtastic.core.network.radio.TCPInterface
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.repository.RadioTransportFactory

class DesktopRadioTransportFactory(
    private val scanner: BleScanner,
    private val bluetoothRepository: BluetoothRepository,
    private val connectionFactory: BleConnectionFactory,
    private val dispatchers: CoroutineDispatchers,
) : RadioTransportFactory {

    override val supportedDeviceTypes: List<DeviceType> = listOf(DeviceType.TCP, DeviceType.BLE, DeviceType.USB)

    override fun isMockInterface(): Boolean = false

    override fun isAddressValid(address: String?): Boolean {
        val spec = address?.getOrNull(0) ?: return false
        return spec == InterfaceId.TCP.id ||
            spec == InterfaceId.SERIAL.id ||
            spec == InterfaceId.BLUETOOTH.id ||
            address.startsWith("!")
    }

    override fun toInterfaceAddress(interfaceId: InterfaceId, rest: String): String = "${interfaceId.id}$rest"

    override fun createTransport(address: String, service: RadioInterfaceService): RadioTransport =
        if (address.startsWith(InterfaceId.TCP.id)) {
            TCPInterface(service, dispatchers, address.removePrefix(InterfaceId.TCP.id.toString()))
        } else if (address.startsWith(InterfaceId.SERIAL.id)) {
            SerialTransport(address.removePrefix(InterfaceId.SERIAL.id.toString()), service)
        } else if (address.startsWith(InterfaceId.BLUETOOTH.id)) {
            DesktopBleInterface(
                serviceScope = service.serviceScope,
                scanner = scanner,
                bluetoothRepository = bluetoothRepository,
                connectionFactory = connectionFactory,
                service = service,
                address = address.removePrefix(InterfaceId.BLUETOOTH.id.toString()),
            )
        } else {
            val stripped = if (address.startsWith("!")) address.removePrefix("!") else address
            DesktopBleInterface(
                serviceScope = service.serviceScope,
                scanner = scanner,
                bluetoothRepository = bluetoothRepository,
                connectionFactory = connectionFactory,
                service = service,
                address = stripped,
            )
        }
}
