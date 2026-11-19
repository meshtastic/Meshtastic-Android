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

import co.touchlab.kermit.Logger
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.model.util.normalizeWebSocketUrl
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

    init {
        if (!bluetoothRepository.isSupported) Logger.w { "No Bluetooth LE on this hardware; BLE addresses are refused" }
    }

    /** Whether a `t` address can open; the Network pane can exist without it, as the WebSocket link does on web. */
    protected open val isRawTcpSupported: Boolean
        get() = DeviceType.TCP in supportedDeviceTypes

    override fun isAddressValid(address: String?): Boolean {
        val spec = address?.firstOrNull() ?: return false
        return when (spec) {
            // A saved TCP address restored where the platform has no raw TCP (the browser) is kept but never armed.
            InterfaceId.TCP.id -> isRawTcpSupported

            InterfaceId.WEBSOCKET.id -> DeviceType.TCP in supportedDeviceTypes

            // A saved serial address restored onto hardware with no USB host is kept but never armed.
            InterfaceId.SERIAL.id -> isSerialSupported

            // A saved BLE address restored onto hardware with no Bluetooth LE is kept but never armed.
            InterfaceId.BLUETOOTH.id,
            '!',
            -> bluetoothRepository.isSupported

            // Virtual transports stay inadmissible until deliberately enabled: `connections?address=m` is reachable
            // from any web page through the verified meshtastic.org app link, so a drive-by deep link must not be able
            // to swap a user's real radio for a fake one.
            InterfaceId.MOCK.id,
            InterfaceId.REPLAY.id,
            -> mockTransportEnabled.value

            else -> isPlatformAddressValid(address)
        }
    }

    /** Whether this hardware can host a USB serial radio. */
    protected open val isSerialSupported: Boolean = true

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

                // Common to every platform: the transport brings its own Ktor CIO client. Normalizing here also gives a
                // saved or linked bare `ws://host` firmware's path.
                address.startsWith(InterfaceId.WEBSOCKET.id) -> {
                    val url = address.removePrefix(InterfaceId.WEBSOCKET.id.toString())
                    WebSocketRadioTransport(
                        callback = service,
                        scope = service.serviceScope,
                        url = normalizeWebSocketUrl(url) ?: url,
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
