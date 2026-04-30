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
package org.meshtastic.core.network.radio

import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.repository.RadioTransportFactory

/**
 * Common base class for platform [RadioTransportFactory] implementations. Handles KMP-friendly transports (BLE) while
 * delegating platform-specific ones (like TCP, USB/Serial and Mocks) to the abstract [createPlatformTransport].
 */
abstract class BaseRadioTransportFactory(
    protected val scanner: BleScanner,
    protected val bluetoothRepository: BluetoothRepository,
    protected val connectionFactory: BleConnectionFactory,
    protected val dispatchers: CoroutineDispatchers,
) : RadioTransportFactory {

    override fun isAddressValid(address: String?): Boolean {
        val spec = address?.firstOrNull() ?: return false
        return when (spec) {
            InterfaceId.TCP.id,
            InterfaceId.SERIAL.id,
            InterfaceId.BLUETOOTH.id,
            InterfaceId.MOCK.id,
            '!',
            -> true

            else -> isPlatformAddressValid(address)
        }
    }

    protected open fun isPlatformAddressValid(address: String): Boolean = false

    override fun toInterfaceAddress(interfaceId: InterfaceId, rest: String): String = "${interfaceId.id}$rest"

    override fun createTransport(address: String, service: RadioInterfaceService): RadioTransport {
        val transport =
            when {
                address.startsWith(InterfaceId.BLUETOOTH.id) || address.startsWith("!") -> {
                    val bleAddress = address.removePrefix(InterfaceId.BLUETOOTH.id.toString()).removePrefix("!")
                    BleRadioTransport(
                        scope = service.serviceScope,
                        scanner = scanner,
                        bluetoothRepository = bluetoothRepository,
                        connectionFactory = connectionFactory,
                        callback = service,
                        address = bleAddress,
                    )
                }

                else -> createPlatformTransport(address, service)
            }
        transport.start()
        return transport
    }

    /** Delegate to platform for Mock, TCP, or Serial/USB transports. */
    protected abstract fun createPlatformTransport(address: String, service: RadioInterfaceService): RadioTransport
}
