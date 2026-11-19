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
@file:Suppress("UnusedParameter")

package org.meshtastic.feature.connections.domain.usecase

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.await
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single
import org.meshtastic.core.network.serial.GrantedSerialPort
import org.meshtastic.core.network.serial.WebSerialPortRegistry
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.getStringSuspend
import org.meshtastic.core.resources.serial_port_chip
import org.meshtastic.core.resources.serial_port_unknown
import org.meshtastic.core.resources.serial_port_usb_ids
import org.meshtastic.feature.connections.model.DeviceListEntry
import org.meshtastic.feature.connections.model.WasmJsUsbDeviceData
import kotlin.coroutines.coroutineContext
import kotlin.js.Promise
import kotlin.time.Duration.Companion.seconds

/**
 * wasmJs [UsbScanner], backed by the Web Serial API. [scanUsbDevices] only lists ports the user has already granted
 * this page access to (`navigator.serial.getPorts()`, no gesture required) — discovering a never-before-seen device
 * needs [requestNewDevice] instead, which shows the browser's picker (see [WebSerialPortRegistry]'s KDoc for why the
 * two are split).
 */
@Single
class WasmJsUsbScanner(private val registry: WebSerialPortRegistry) : UsbScanner {
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)
    private var listedPortIds: Set<String> = emptySet()

    override fun scanUsbDevices(): Flow<List<DeviceListEntry.Usb>> = flow {
        while (coroutineContext.isActive) {
            val ports = registry.refreshGrantedPorts()
            listedPortIds = ports.mapTo(mutableSetOf()) { it.id }
            emit(ports.map { it.toEntry() })
            withTimeoutOrNull(POLL_INTERVAL) { refreshRequests.receive() }
        }
    }
        .distinctUntilChanged()

    override fun needsExplicitDeviceRequest(): Boolean = true

    /**
     * Shows the browser's port chooser, filtered to [MESHTASTIC_USB_IDS] unless [showAll], and returns the entry for
     * the port the user picked so the caller can connect to it. `requestPort()` is called before the first suspension,
     * which keeps it inside the click's user activation.
     */
    @Suppress("TooGenericExceptionCaught")
    override suspend fun requestNewDevice(showAll: Boolean): DeviceListEntry.Usb? {
        val request = if (showAll) requestAnyPort() else requestPortMatching(MESHTASTIC_USB_FILTERS_JSON)
        val known = listedPortIds
        val picked =
            try {
                request.await<JsAny>()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // Dismissing the chooser rejects with NotFoundError; there is nothing to report.
                Logger.d(e) { "Serial port chooser closed without a port" }
                return null
            }
        val granted = registry.refreshGrantedPorts()
        listedPortIds = granted.mapTo(mutableSetOf()) { it.id }
        refreshRequests.trySend(Unit)
        val vendorId = usbVendorId(picked)
        val productId = usbProductId(picked)
        val port =
            granted.singleOrNull { it.id !in known }
                ?: granted.singleOrNull { it.vendorId == vendorId && it.productId == productId }
        return port?.toEntry()
    }

    private suspend fun GrantedSerialPort.toEntry() = DeviceListEntry.Usb(
        usbData = WasmJsUsbDeviceData(id),
        name = describePort(vendorId, productId),
        fullAddress = "s$id",
        // The browser's chooser is the only grant there is, and every port listed has been through it.
        bonded = true,
        node = null,
    )

    companion object {
        private val POLL_INTERVAL = 2.seconds
    }
}

/** A board's own name for its USB ID, a bridge chip's name, or the raw USB ID, in that order. */
internal suspend fun describePort(vendorId: Int, productId: Int): String {
    if (vendorId == 0 && productId == 0) return getStringSuspend(Res.string.serial_port_unknown)
    val known = MESHTASTIC_USB_IDS.firstOrNull { it.matches(vendorId, productId) }
    return when {
        known == null -> getStringSuspend(Res.string.serial_port_usb_ids, usbIdLabel(vendorId, productId))
        known.isBoard -> known.name
        else -> getStringSuspend(Res.string.serial_port_chip, known.name)
    }
}

private const val HEX_RADIX = 16
private const val USB_ID_DIGITS = 4

private fun usbIdLabel(vendorId: Int, productId: Int): String =
    "${vendorId.toString(HEX_RADIX).padStart(USB_ID_DIGITS, '0')}:" +
        productId.toString(HEX_RADIX).padStart(USB_ID_DIGITS, '0')

private fun requestPortMatching(filtersJson: String): Promise<JsAny> =
    js("navigator.serial.requestPort({ filters: JSON.parse(filtersJson) })")

private fun requestAnyPort(): Promise<JsAny> = js("navigator.serial.requestPort()")

private fun usbVendorId(port: JsAny): Int = js("(port.getInfo().usbVendorId || 0)")

private fun usbProductId(port: JsAny): Int = js("(port.getInfo().usbProductId || 0)")
