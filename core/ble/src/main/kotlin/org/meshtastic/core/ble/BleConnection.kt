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
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import no.nordicsemi.android.common.core.simpleSharedFlow
import no.nordicsemi.kotlin.ble.client.RemoteCharacteristic
import no.nordicsemi.kotlin.ble.client.RemoteService
import no.nordicsemi.kotlin.ble.client.RemoteServices
import no.nordicsemi.kotlin.ble.client.android.CentralManager
import no.nordicsemi.kotlin.ble.client.android.ConnectionPriority
import no.nordicsemi.kotlin.ble.client.android.Peripheral
import no.nordicsemi.kotlin.ble.core.ConnectionState
import kotlin.uuid.Uuid

private const val SERVICE_DISCOVERY_TIMEOUT_MS = 10_000L

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
     * Connects to the given [Peripheral]. Note that this method returns as soon as the connection attempt is initiated.
     * Use [connectAndAwait] if you need to wait for the connection to be established.
     *
     * @param p The peripheral to connect to.
     */
    suspend fun connect(p: Peripheral) = withContext(NonCancellable) {
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

    /**
     * Connects to the given [Peripheral] and waits for a terminal state (Connected or Disconnected).
     *
     * @param p The peripheral to connect to.
     * @param timeoutMs The maximum time to wait for a connection in milliseconds.
     * @return The final [ConnectionState].
     * @throws kotlinx.coroutines.TimeoutCancellationException if the timeout is reached.
     */
    suspend fun connectAndAwait(p: Peripheral, timeoutMs: Long): ConnectionState {
        connect(p)
        return withTimeout(timeoutMs) {
            connectionState.first { it is ConnectionState.Connected || it is ConnectionState.Disconnected }
        }
    }

    /** A flow of discovered services. Useful for reacting to "Service Changed" indications. */
    val services: SharedFlow<List<RemoteService>> =
        _connectionState
            .asSharedFlow()
            .filter { it is ConnectionState.Connected }
            .flatMapLatest {
                peripheral?.services()?.onEach {
                    if (it is RemoteServices.Failed) {
                        Logger.w { "[$tag] Service discovery failed: ${it.reason}" }
                    }
                }?.mapNotNull { (it as? RemoteServices.Discovered)?.services }
                    ?: flowOf(emptyList())
            }
            .filterNotNull()
            .shareIn(scope, SharingStarted.WhileSubscribed(), replay = 1)

    /** Discovers characteristics for a specific service. */
    suspend fun discoverCharacteristics(
        serviceUuid: Uuid,
        requiredUuids: List<Uuid>,
        optionalUuids: List<Uuid> = emptyList(),
    ): Map<Uuid, RemoteCharacteristic>? {
        val p = peripheral ?: return null

        return retryBleOperation(tag = tag) {
            val allRequested = requiredUuids + optionalUuids
            val serviceList =
                withTimeout(SERVICE_DISCOVERY_TIMEOUT_MS) {
                    p.services(listOf(serviceUuid))
                        .filter { it is RemoteServices.Discovered || it is RemoteServices.Failed }
                        .first()
                        .let {
                            if (it is RemoteServices.Failed) {
                                throw Exception("Discovery failed: ${it.reason}")
                            }
                            (it as RemoteServices.Discovered).services
                        }
                }
            val service = serviceList.find { it.uuid == serviceUuid } ?: return@retryBleOperation null

            val result = mutableMapOf<Uuid, RemoteCharacteristic>()
            for (uuid in allRequested) {
                val char = service.characteristics.find { it.uuid == uuid }
                if (char != null) {
                    result[uuid] = char
                }
            }

            val hasAllRequired = requiredUuids.all { result.containsKey(it) }
            if (hasAllRequired) result else null
        }
    }

    private fun observePeripheralDetails(p: Peripheral) {
        p.phy.onEach { phy -> Logger.i { "[$tag] BLE PHY changed to $phy" } }.launchIn(scope)

        p.connectionParameters
            .onEach { params -> Logger.i { "[$tag] BLE connection parameters changed to $params" } }
            .launchIn(scope)
    }

    /** Disconnects from the current peripheral. */
    suspend fun disconnect() = withContext(NonCancellable) {
        stateJob?.cancel()
        stateJob = null
        peripheral?.disconnect()
        peripheral = null
    }
}
