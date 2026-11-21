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
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.repository.RadioTransportFactory

/** Browser implementation of [RadioTransportFactory] for SDK BLE, WebSocket and Web Serial links. */
@Single(binds = [RadioTransportFactory::class])
class WasmJsRadioTransportFactory(
    bluetoothRepository: BluetoothRepository,
    private val sdk: WebSdkRadioTransportFactory,
    private val virtual: VirtualRadioSessionFactory,
) : BaseRadioTransportFactory(bluetoothRepository) {

    // Network is the WebSocket phone link alone: raw TCP never reaches a browser.
    override val supportedDeviceTypes: List<DeviceType> = listOf(DeviceType.BLE, DeviceType.TCP, DeviceType.USB)

    override val isRawTcpSupported: Boolean = false

    // No unlock gesture and no demo entry exist on web yet (mirrors desktop): `connections?address=m` is reachable
    // from any page via the verified app link, so the virtual transports stay inadmissible until a web-specific
    // gesture is deliberately added.
    override val mockTransportEnabled: StateFlow<Boolean> = MutableStateFlow(false)

    // No bundled capture asset ships on web (no context.assets equivalent).
    override val isReplayTransportAvailable: Boolean = false

    override fun createWebSocketTransport(url: String, service: RadioInterfaceService): RadioTransport =
        sdk.webSocket(url, service)

    override fun createBleTransport(address: String, service: RadioInterfaceService): RadioTransport =
        sdk.ble(address, service)

    override fun createPlatformTransport(address: String, service: RadioInterfaceService): RadioTransport {
        val interfaceId = address.firstOrNull()?.let { InterfaceId.forIdChar(it) }
        val rest = address.substring(1)

        return when (interfaceId) {
            InterfaceId.MOCK -> virtual.demo(rest, service)

            InterfaceId.REPLAY -> {
                Logger.w { "Replay device selected but no capture asset ships on web — falling back to mock" }
                virtual.demo(rest, service)
            }

            InterfaceId.TCP ->
                error(
                    "TCP transport is not supported on web: browsers cannot open raw sockets " +
                        "(permanent sandbox limitation)",
                )

            InterfaceId.SERIAL -> sdk.serial(rest, service)

            InterfaceId.NOP,
            null,
            -> NopRadioTransport(rest)

            InterfaceId.BLUETOOTH -> error("BLE addresses should be handled by BaseRadioTransportFactory")

            InterfaceId.WEBSOCKET -> error("WebSocket addresses should be handled by BaseRadioTransportFactory")
        }
    }
}
