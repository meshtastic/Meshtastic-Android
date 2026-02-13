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
package org.meshtastic.core.ble

import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import no.nordicsemi.android.common.core.simpleSharedFlow
import no.nordicsemi.kotlin.ble.client.RemoteCharacteristic
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.client.android.ConnectionPriority
import no.nordicsemi.kotlin.ble.client.android.Peripheral
import no.nordicsemi.kotlin.ble.core.ConnectionState
import kotlin.uuid.Uuid

/**
 * Encapsulates a BLE connection to a [Peripheral]. Handles connection lifecycle, state monitoring, and service
 * discovery.
 *
 * @param centralManager The Nordic [CentralManager] to use for connection.
 * @param scope The [CoroutineScope] in which to monitor connection state.
 * @param tag A tag for logging.
 */
class BleConnection(
    private val centralManager: CentralManager,
    private val scope: CoroutineScope,
    private val tag: String = "BLE",
) {
    /** The currently connected [Peripheral], or null if not connected. */
    var peripheral: Peripheral? = null
        private set

    private val _connectionState = simpleSharedFlow<ConnectionState>()

    /** A flow of [ConnectionState] changes for the current [peripheral]. */
    val connectionState: SharedFlow<ConnectionState> = _connectionState.asSharedFlow()

    private var stateJob: Job? = null

    /**
     * Connects to the given [Peripheral].
     *
     * @param p The peripheral to connect to.
     */
    suspend fun connect(p: Peripheral) {
        stateJob?.cancel()
        peripheral = p

        centralManager.connect(
            peripheral = p,
            options = CentralManager.ConnectionOptions.AutoConnect(automaticallyRequestHighestValueLength = true),
        )

        stateJob =
            p.state
                .onEach { state ->
                    Logger.d { "[$tag] Connection state changed to $state" }

                    if (state is ConnectionState.Connected) {
                        p.requestConnectionPriority(ConnectionPriority.HIGH)
                        observePeripheralDetails(p)
                    }

                    _connectionState.emit(state)
                }
                .launchIn(scope)
    }

    /** Discovers characteristics for a specific service with retries. */
    @Suppress("ReturnCount")
    suspend fun discoverCharacteristics(
        serviceUuid: Uuid,
        characteristicUuids: List<Uuid>,
    ): Map<Uuid, RemoteCharacteristic>? = retryBleOperation(tag = tag) {
        val p = peripheral ?: return@retryBleOperation null
        val services = p.services(listOf(serviceUuid)).filterNotNull().first()
        val service = services.find { it.uuid == serviceUuid } ?: return@retryBleOperation null

        val result = mutableMapOf<Uuid, RemoteCharacteristic>()
        for (uuid in characteristicUuids) {
            val char = service.characteristics.find { it.uuid == uuid }
            if (char != null) {
                result[uuid] = char
            }
        }
        return@retryBleOperation if (result.size == characteristicUuids.size) result else null
    }

    private fun observePeripheralDetails(p: Peripheral) {
        p.phy.onEach { phy -> Logger.i { "[$tag] BLE PHY changed to $phy" } }.launchIn(scope)

        p.connectionParameters
            .onEach { params -> Logger.i { "[$tag] BLE connection parameters changed to $params" } }
            .launchIn(scope)
    }

    /** Disconnects from the current peripheral. */
    suspend fun disconnect() {
        stateJob?.cancel()
        stateJob = null
        peripheral?.disconnect()
        peripheral = null
    }
}
