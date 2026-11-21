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
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.model.util.anonymize
import org.meshtastic.core.network.radio.BaseRadioTransportFactory
import org.meshtastic.core.network.radio.DesktopSdkTransportFactory
import org.meshtastic.core.network.radio.VirtualRadioSessionFactory
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.core.repository.RadioTransportFactory
import org.meshtastic.desktop.DesktopBuildConfig

/**
 * Desktop [RadioTransportFactory] for SDK radio links and the app demo radio.
 *
 * Registered manually in [DesktopRuntimeModule] — do NOT add @Single to avoid double-registration with
 * the @ComponentScan("org.meshtastic.desktop") in DesktopDiModule.
 */
class DesktopRadioTransportFactory(
    bluetoothRepository: BluetoothRepository,
    private val sdk: DesktopSdkTransportFactory,
    private val virtual: VirtualRadioSessionFactory,
) : BaseRadioTransportFactory(bluetoothRepository) {

    override val supportedDeviceTypes: List<DeviceType> = listOf(DeviceType.TCP, DeviceType.BLE, DeviceType.USB)

    // Desktop has no unlock gesture, so Demo Mode is a debug-build feature: it admits the `m` addresses, including
    // the hidden showcase, and offers the demo entry in the picker.
    override val mockTransportEnabled: StateFlow<Boolean> = MutableStateFlow(DesktopBuildConfig.IS_DEBUG)

    /** Desktop bundles no capture asset, and [createPlatformTransport] does not wire a replay address. */
    override val isReplayTransportAvailable: Boolean = false

    override fun createBleTransport(address: String, service: RadioInterfaceService): RadioTransport =
        sdk.create(InterfaceId.BLUETOOTH, address, service)

    override fun createWebSocketTransport(url: String, service: RadioInterfaceService): RadioTransport =
        sdk.create(InterfaceId.WEBSOCKET, url, service)

    override fun createPlatformTransport(address: String, service: RadioInterfaceService): RadioTransport = when {
        address.startsWith(InterfaceId.MOCK.id) -> virtual.demo(address.substring(1), service)
        address.startsWith(InterfaceId.TCP.id) -> sdk.create(InterfaceId.TCP, address.substring(1), service)
        address.startsWith(InterfaceId.SERIAL.id) -> sdk.create(InterfaceId.SERIAL, address.substring(1), service)
        else -> error("Unsupported transport for address: ${address.anonymize()}")
    }
}
