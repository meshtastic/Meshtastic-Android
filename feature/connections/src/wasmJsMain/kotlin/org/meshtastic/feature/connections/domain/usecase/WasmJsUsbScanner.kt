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
package org.meshtastic.feature.connections.domain.usecase

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import org.koin.core.annotation.Single
import org.meshtastic.core.network.serial.WebSerialPortRegistry
import org.meshtastic.feature.connections.model.DeviceListEntry
import org.meshtastic.feature.connections.model.WasmJsUsbDeviceData
import kotlin.coroutines.coroutineContext

/**
 * wasmJs [UsbScanner], backed by the Web Serial API. [scanUsbDevices] only lists ports the user has already granted
 * this page access to (`navigator.serial.getPorts()`, no gesture required) — discovering a never-before-seen device
 * needs [requestNewDevice] instead, which shows the browser's picker (see [WebSerialPortRegistry]'s KDoc for why the
 * two are split).
 */
@Single
class WasmJsUsbScanner(private val registry: WebSerialPortRegistry) : UsbScanner {
    override fun scanUsbDevices(): Flow<List<DeviceListEntry.Usb>> = flow {
        while (coroutineContext.isActive) {
            val ports = registry.refreshGrantedPorts()
            emit(
                ports.map { port ->
                    DeviceListEntry.Usb(
                        usbData = WasmJsUsbDeviceData(port.id),
                        name = describePort(port.vendorId, port.productId),
                        fullAddress = "s${port.id}",
                        // Web Serial's own picker is the only "pairing" step that exists — every port this
                        // registry
                        // knows about was already explicitly granted by the user, so there is no separate
                        // unbonded
                        // state to represent here.
                        bonded = true,
                        node = null,
                    )
                },
            )
            delay(POLL_INTERVAL_MS)
        }
    }
        .distinctUntilChanged()

    override fun needsExplicitDeviceRequest(): Boolean = true

    override suspend fun requestNewDevice() {
        registry.requestNewPort()
    }

    private fun describePort(vendorId: Int, productId: Int): String = if (vendorId == 0 && productId == 0) {
        "Serial device"
    } else {
        "Serial ${vendorId.toString(HEX_RADIX)}:${productId.toString(HEX_RADIX)}"
    }

    companion object {
        private const val POLL_INTERVAL_MS = 2000L
        private const val HEX_RADIX = 16
    }
}
