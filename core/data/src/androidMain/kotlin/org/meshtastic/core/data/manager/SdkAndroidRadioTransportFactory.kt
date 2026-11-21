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
package org.meshtastic.core.data.manager

import android.app.Application
import android.hardware.usb.UsbDevice
import org.koin.core.annotation.Single
import org.meshtastic.core.network.radio.AndroidSdkTransportFactory
import org.meshtastic.core.network.transport.parseTcpRadioAddress
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.RadioTransport
import org.meshtastic.sdk.transport.ble.BleResilience
import org.meshtastic.sdk.transport.ble.BleTransport
import org.meshtastic.sdk.transport.serial.AndroidUsbSerialPorts
import org.meshtastic.sdk.transport.tcp.TcpTransport

@Single(binds = [AndroidSdkTransportFactory::class])
internal class SdkAndroidRadioTransportFactory(
    private val application: Application,
    private val sessions: SdkPhoneApiSessions,
) : AndroidSdkTransportFactory {
    override fun tcp(address: String, service: RadioInterfaceService): RadioTransport = sessions.open(service) {
        val (host, port) = parseTcpRadioAddress(address)
        TcpTransport(host, port)
    }

    override fun webSocket(url: String, service: RadioInterfaceService): RadioTransport =
        sessions.webSocket(url, service)

    override fun ble(address: String, service: RadioInterfaceService): RadioTransport = sessions.open(service) {
        BleTransport(
            address = address.uppercase(),
            resilience = BleResilience(bondFirst = true, autoConnectFallback = true, refreshStaleGattCache = true),
            refreshGattCacheOnConnect = service::consumeGattCacheInvalidationRequest,
            builderAction = {},
        )
    }

    override fun usb(device: UsbDevice, service: RadioInterfaceService): RadioTransport =
        sessions.open(service) { AndroidUsbSerialPorts.open(application, device) }
}
