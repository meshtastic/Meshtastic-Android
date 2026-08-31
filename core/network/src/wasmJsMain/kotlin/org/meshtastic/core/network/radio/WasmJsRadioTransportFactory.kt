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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.annotation.Single
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.network.serial.WebSerialPortRegistry
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.repository.RadioTransportFactory

/**
 * wasmJs (browser) implementation of [RadioTransportFactory]. BLE is handled entirely by [BaseRadioTransportFactory]
 * via `core:ble`'s Web Bluetooth actuals. Mock/Replay are commonMain-portable and created directly here, same as every
 * other platform. Serial/USB is backed by the Web Serial API via [WasmJsSerialTransport] and [WebSerialPortRegistry].
 * TCP has no browser equivalent at all and fails loudly rather than silently degrading: it is a permanent sandbox
 * impossibility (browsers cannot open raw sockets), unlike Serial/USB which merely needed its own transport written.
 */
@Single(binds = [RadioTransportFactory::class])
class WasmJsRadioTransportFactory(
    scanner: BleScanner,
    bluetoothRepository: BluetoothRepository,
    connectionFactory: BleConnectionFactory,
    dispatchers: CoroutineDispatchers,
    private val serialPortRegistry: WebSerialPortRegistry,
) : BaseRadioTransportFactory(scanner, bluetoothRepository, connectionFactory, dispatchers) {

    override val supportedDeviceTypes: List<DeviceType> = listOf(DeviceType.BLE, DeviceType.USB)

    // No unlock gesture and no demo entry exist on web yet (mirrors desktop): `connections?address=m` is reachable
    // from any page via the verified app link, so the virtual transports stay inadmissible until a web-specific
    // gesture is deliberately added.
    override val mockTransportEnabled: StateFlow<Boolean> = MutableStateFlow(false)

    // No bundled capture asset ships on web (no context.assets equivalent).
    override val isReplayTransportAvailable: Boolean = false

    override fun createPlatformTransport(address: String, service: RadioInterfaceService): RadioTransport {
        val interfaceId = address.firstOrNull()?.let { InterfaceId.forIdChar(it) }
        val rest = address.substring(1)

        return when (interfaceId) {
            InterfaceId.MOCK -> MockRadioTransport(callback = service, scope = service.serviceScope, address = rest)

            InterfaceId.REPLAY -> {
                Logger.w { "Replay device selected but no capture asset ships on web — falling back to mock" }
                MockRadioTransport(callback = service, scope = service.serviceScope, address = rest)
            }

            InterfaceId.TCP ->
                error(
                    "TCP transport is not supported on web: browsers cannot open raw sockets " +
                        "(permanent sandbox limitation)",
                )

            InterfaceId.SERIAL ->
                WasmJsSerialTransport(
                    portId = rest,
                    registry = serialPortRegistry,
                    callback = service,
                    scope = service.serviceScope,
                )

            InterfaceId.NOP,
            null,
            -> NopRadioTransport(rest)

            InterfaceId.BLUETOOTH -> error("BLE addresses should be handled by BaseRadioTransportFactory")
        }
    }
}
