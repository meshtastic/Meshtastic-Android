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

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.data.radio.sim.MockRadioTransport
import org.meshtastic.core.data.radio.sim.ReplayRadioTransport
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.model.util.normalizeWebSocketUrl
import org.meshtastic.core.repository.RadioLink
import org.meshtastic.core.repository.RadioLinks
import org.meshtastic.sdk.TransportIdentity
import org.meshtastic.sdk.transport.websocket.WebSocketTransport

/** Address admission and prefix dispatch for every platform's [RadioLinks]. */
abstract class BaseRadioLinks(
    private val bluetoothRepository: BluetoothRepository,
    protected val pipeline: RadioSessionPipeline,
) : RadioLinks {

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

            else -> false
        }
    }

    /** Whether this hardware can host a USB serial radio. */
    protected open val isSerialSupported: Boolean = true

    override fun toInterfaceAddress(interfaceId: InterfaceId, rest: String): String = "${interfaceId.id}$rest"

    override fun open(address: String, scope: CoroutineScope): RadioLink? {
        val rest = address.drop(1)
        return when {
            address.startsWith(InterfaceId.BLUETOOTH.id) || address.startsWith("!") -> openBle(rest, scope)

            // Normalizing here also gives a saved or linked bare `ws://host` firmware's path.
            address.startsWith(InterfaceId.WEBSOCKET.id) -> openWebSocket(normalizeWebSocketUrl(rest) ?: rest, scope)

            else ->
                when (address.firstOrNull()?.let(InterfaceId::forIdChar)) {
                    InterfaceId.MOCK -> openMock(rest, scope)
                    InterfaceId.REPLAY -> openReplay(rest, scope)
                    InterfaceId.TCP -> openTcp(rest, scope)
                    InterfaceId.SERIAL -> openSerial(rest, scope)
                    else -> null
                }
        }
    }

    /** Opens a Bluetooth link for a saved address with its interface prefix removed. */
    protected abstract fun openBle(address: String, scope: CoroutineScope): RadioLink?

    /** Opens a WebSocket link for a normalized radio URL; one transport keeps the SDK's state across attempts. */
    protected open fun openWebSocket(url: String, scope: CoroutineScope): RadioLink? {
        val transport = WebSocketTransport(url)
        return pipeline.link(
            identity = transport.identity,
            ble = false,
            scope = scope,
            provider = { _, _ -> transport },
            failureReason = { webSocketFailureReason(it, url) },
        )
    }

    protected open fun openTcp(rest: String, scope: CoroutineScope): RadioLink? = null

    protected open fun openSerial(rest: String, scope: CoroutineScope): RadioLink? = null

    /** A virtual radio, fresh for every attempt. */
    protected fun openMock(rest: String, scope: CoroutineScope): RadioLink = pipeline.link(
        identity = TransportIdentity("mock:$rest"),
        ble = false,
        scope = scope,
        provider = { _, _ -> MockRadioTransport(scope, rest) },
    )

    /** Replays a bundled packet capture; a build without one opens the plain mock. */
    protected open fun openReplay(rest: String, scope: CoroutineScope): RadioLink {
        val frames = replayFrames() ?: return openMock(rest, scope)
        return pipeline.link(
            identity = TransportIdentity("replay:$rest"),
            ble = false,
            scope = scope,
            provider = { _, _ -> ReplayRadioTransport(scope, rest, frames) },
        )
    }

    /** The bundled capture a replay radio plays, or null where none ships. */
    protected open fun replayFrames(): ByteArray? = null

    /** Waits for Bluetooth to be on: a link that drops when the adapter turns off resumes once it is back. */
    protected suspend fun awaitBluetooth() {
        bluetoothRepository.state.first { it.enabled }
    }
}
