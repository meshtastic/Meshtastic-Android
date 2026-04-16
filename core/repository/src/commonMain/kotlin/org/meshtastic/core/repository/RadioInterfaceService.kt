/*
 * Copyright (c) 2025-2026 Meshtastic LLC
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
package org.meshtastic.core.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.model.MeshActivity

/**
 * Interface for the low-level radio interface that handles raw byte communication.
 *
 * This is the **transport layer** — it manages the raw hardware connection (BLE, TCP, Serial, USB) to a Meshtastic
 * radio. Its [connectionState] reflects whether the physical link is up or down, **before** any handshake or
 * config-loading logic is applied.
 *
 * **Important:** UI and feature modules should **never** observe [connectionState] directly. Instead, they should use
 * [ServiceRepository.connectionState], which is the canonical app-level connection state that accounts for handshake
 * progress, light-sleep policy, and other higher-level concerns. The only legitimate consumer of this transport-level
 * flow is [MeshConnectionManager], which bridges transport state changes into the app-level
 * [ServiceRepository.connectionState].
 *
 * @see ServiceRepository.connectionState
 */
interface RadioInterfaceService : RadioTransportCallback {
    /** The device types supported by this platform's radio interface. */
    val supportedDeviceTypes: List<DeviceType>

    /**
     * Transport-level connection state of the radio hardware.
     *
     * This flow reflects the raw state of the physical link (BLE, TCP, Serial, USB):
     * - [ConnectionState.Connected] — the transport link is established
     * - [ConnectionState.Disconnected] — the transport link is down (permanent)
     * - [ConnectionState.DeviceSleep] — the transport link is down (transient, device sleeping)
     *
     * **This is NOT the canonical app-level connection state.** The transport may report [ConnectionState.Connected]
     * while the app is still performing the mesh handshake (config + node-info exchange), during which the app-level
     * state remains [ConnectionState.Connecting].
     *
     * Only [MeshConnectionManager] should observe this flow. All other consumers (ViewModels, feature modules, UI) must
     * use [ServiceRepository.connectionState].
     *
     * @see ServiceRepository.connectionState
     */
    val connectionState: StateFlow<ConnectionState>

    /** Flow of the current device address. */
    val currentDeviceAddressFlow: StateFlow<String?>

    /** Whether we are currently using a mock transport. */
    fun isMockTransport(): Boolean

    /**
     * Flow of raw data received from the radio.
     *
     * Emissions preserve the order in which bytes arrived from the hardware — this is required because the firmware
     * handshake (initial config packet ordering) depends on strict FIFO delivery. Implementations MUST guarantee
     * ordering; do not swap in a [SharedFlow] without preserving order.
     */
    val receivedData: Flow<ByteArray>

    /** Flow of radio activity events. */
    val meshActivity: SharedFlow<MeshActivity>

    /**
     * Drains any bytes currently buffered in [receivedData] without emitting them to collectors.
     *
     * Callers invoke this before attaching a fresh collector after a stop/start cycle so stale bytes buffered while no
     * collector was attached do not get replayed ahead of the next session's handshake.
     */
    fun resetReceivedBuffer()

    /** Sends a raw byte array to the radio. */
    fun sendToRadio(bytes: ByteArray)

    /** Initiates the connection to the radio. */
    fun connect()

    /** Returns the current device address. */
    fun getDeviceAddress(): String?

    /** Sets the device address to connect to. */
    fun setDeviceAddress(deviceAddr: String?): Boolean

    /** Constructs a full radio address for the specific interface type. */
    fun toInterfaceAddress(interfaceId: InterfaceId, rest: String): String

    /** Flow of user-facing connection error messages (e.g. permission failures). */
    val connectionError: SharedFlow<String>

    /** The scope in which interface-related coroutines should run. */
    val serviceScope: CoroutineScope
}
