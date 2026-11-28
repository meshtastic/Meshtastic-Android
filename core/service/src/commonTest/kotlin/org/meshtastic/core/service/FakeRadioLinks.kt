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
package org.meshtastic.core.service

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import org.meshtastic.core.model.DeviceType
import org.meshtastic.core.model.InterfaceId
import org.meshtastic.core.repository.RadioLink
import org.meshtastic.core.repository.RadioLinks
import org.meshtastic.core.repository.RadioSessionHost
import org.meshtastic.core.repository.TransportDisconnectReason
import org.meshtastic.sdk.ConnectionState
import org.meshtastic.sdk.InMemoryStorageProvider
import org.meshtastic.sdk.MeshtasticException
import org.meshtastic.sdk.RadioClient
import org.meshtastic.sdk.TransportIdentity
import org.meshtastic.sdk.testing.FakeRadioTransport

/** A link whose client state each test sets, with a client that is never connected. */
internal class FakeLink(val address: String) : RadioLink {
    override val client: RadioClient =
        RadioClient.Builder()
            .transport(FakeRadioTransport(TransportIdentity("fake:$address")))
            .storage(InMemoryStorageProvider())
            .build()

    override val connection = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val reasons = mutableMapOf<MeshtasticException, TransportDisconnectReason>()
    var connects = 0
    var disconnects = 0
    var retries = 0
    var gattRefreshes = 0
    var closed = false
    lateinit var host: RadioSessionHost

    override suspend fun connect() {
        connects++
        connection.value = ConnectionState.Connecting(attempt = 1)
    }

    override suspend fun disconnect() {
        disconnects++
        connection.value = ConnectionState.Disconnected
    }

    override fun retryNow(): Boolean {
        retries++
        return true
    }

    override suspend fun run(host: RadioSessionHost) {
        this.host = host
        awaitCancellation()
    }

    override fun refreshGattCacheOnNextConnect(): Boolean {
        val ble = address.startsWith(InterfaceId.BLUETOOTH.id)
        if (ble) gattRefreshes++
        return ble
    }

    override fun failureReason(cause: MeshtasticException): TransportDisconnectReason? = reasons[cause]

    override fun close() {
        closed = true
    }
}

internal class FakeLinks : RadioLinks {
    val opened = mutableListOf<FakeLink>()
    val refused = mutableSetOf<String>()

    override val supportedDeviceTypes = listOf(DeviceType.BLE, DeviceType.TCP, DeviceType.USB)
    override val mockTransportEnabled = MutableStateFlow(false)
    override val isReplayTransportAvailable = false

    override fun isAddressValid(address: String?): Boolean = address != null && address !in refused

    override fun toInterfaceAddress(interfaceId: InterfaceId, rest: String): String = "${interfaceId.id}$rest"

    /** Addresses that pass [isAddressValid] but whose link cannot be built. */
    val unopenable = mutableSetOf<String>()

    override fun open(address: String, scope: CoroutineScope): RadioLink {
        check(address !in unopenable) { "No link can be built for this address" }
        return FakeLink(address).also(opened::add)
    }
}
