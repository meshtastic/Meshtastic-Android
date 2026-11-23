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
package org.meshtastic.core.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.sdk.ConnectionState
import org.meshtastic.sdk.MeshtasticException
import org.meshtastic.sdk.RadioClient

/** The radio links this platform can open, by saved device address. */
interface RadioLinks {
    /** The device types this platform can connect to. */
    val supportedDeviceTypes: List<DeviceType>

    /**
     * Whether the virtual demo radios (`m` mock, `r` replay) may be offered and opened right now. It can flip at
     * runtime (the Android Demo Mode gesture), and the device list and [isAddressValid] must read this one flow.
     */
    val mockTransportEnabled: StateFlow<Boolean>

    /** Whether this build carries the packet capture a replay radio plays; without it `r` opens the plain mock. */
    val isReplayTransportAvailable: Boolean

    /** Whether [address] names a link this platform can open. */
    fun isAddressValid(address: String?): Boolean

    /** The full address for [rest] on [interfaceId]. */
    fun toInterfaceAddress(interfaceId: InterfaceId, rest: String): String

    /** Builds the SDK client for [address] on [scope], or null for an address that opens nothing. */
    fun open(address: String, scope: CoroutineScope): RadioLink?
}

/** One selected radio: its SDK client, which owns connecting and reconnecting, and the app pipeline it feeds. */
interface RadioLink {
    val client: RadioClient

    /** The client's connection state. */
    val connection: StateFlow<ConnectionState>
        get() = client.connection

    /** Connects the client; with auto-reconnect on it returns at the first connect and throws only on a failure. */
    suspend fun connect() = client.connect()

    /** Disconnects the client and waits for its teardown. */
    suspend fun disconnect() = client.disconnect()

    /** Ends the client's backoff wait so its next attempt starts now. */
    fun retryNow(): Boolean = client.retryNow()

    /** Feeds the client's sessions into the app through [host] until cancelled. */
    suspend fun run(host: RadioSessionHost)

    /**
     * Asks the link to refresh Android's cached GATT table on its next connect, after a firmware update. Returns false
     * for a link with no GATT table, which leaves the request for a later Bluetooth link.
     */
    fun refreshGattCacheOnNextConnect(): Boolean = false

    /** What [cause] means for this kind of link in terms the user can act on, or null when it has no such reading. */
    fun failureReason(cause: MeshtasticException): TransportDisconnectReason? = null

    /** Releases what the link's transports hold, after the client has disconnected. */
    fun close() = Unit
}

/** The service side of [RadioLink.run]: app session admission for the SDK sessions of one link. */
interface RadioSessionHost : RadioSessionAuthority {
    /** Admits a new app session for the link's next SDK session, or null once the link is no longer selected. */
    fun openSession(): RadioSessionContext?

    /** Closes admission for [session] and waits for its admitted work. */
    suspend fun closeSession(session: RadioSessionContext)
}
