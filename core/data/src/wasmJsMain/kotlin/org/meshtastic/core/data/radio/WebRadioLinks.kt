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
package org.meshtastic.core.data.radio

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.annotation.Single
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.ble.WebBleScanner
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.network.serial.WebSerialPortRegistry
import org.meshtastic.core.network.serial.WebSerialTransport
import org.meshtastic.core.repository.RadioLink
import org.meshtastic.core.repository.RadioLinks
import org.meshtastic.sdk.TransportIdentity
import org.meshtastic.sdk.TransportSpec
import org.meshtastic.sdk.transport.ble.BleTransport

/** The browser's radio links: Web Bluetooth and Web Serial devices the user granted, and the WebSocket link. */
@Single(binds = [RadioLinks::class])
class WebRadioLinks(
    private val scanner: WebBleScanner,
    private val serialPorts: WebSerialPortRegistry,
    bluetoothRepository: BluetoothRepository,
    pipeline: RadioSessionPipeline,
) : BaseRadioLinks(bluetoothRepository, pipeline) {

    // Network is the WebSocket phone link alone: raw TCP never reaches a browser.
    override val supportedDeviceTypes: List<DeviceType> = listOf(DeviceType.BLE, DeviceType.TCP, DeviceType.USB)

    override val isRawTcpSupported: Boolean = false

    // No unlock gesture and no demo entry exist on web yet (mirrors desktop): `connections?address=m` is reachable
    // from any page via the verified app link, so the virtual transports stay inadmissible.
    override val mockTransportEnabled: StateFlow<Boolean> = MutableStateFlow(false)

    // No bundled capture asset ships on web.
    override val isReplayTransportAvailable: Boolean = false

    /** The granted device is resolved on the first attempt and reused after it. */
    override fun openBle(address: String, scope: CoroutineScope): RadioLink {
        val transport = atomic<BleTransport?>(null)
        return pipeline.link(
            identity = TransportIdentity.of(TransportSpec.Ble(address)),
            ble = true,
            scope = scope,
            provider = { _, _ ->
                transport.value
                    ?: run {
                        val peripheral =
                            checkNotNull(scanner.grantedPeripheral(address)) {
                                "Web Bluetooth device is no longer granted"
                            }
                        BleTransport(peripheral, address, scope.coroutineContext).also { transport.value = it }
                    }
            },
            close = { transport.value?.shutdown() },
        )
    }

    override fun openSerial(rest: String, scope: CoroutineScope): RadioLink = pipeline.link(
        identity = TransportIdentity("serial-web:$rest"),
        ble = false,
        scope = scope,
        provider = { _, _ -> WebSerialTransport(rest, serialPorts) },
    )
}
