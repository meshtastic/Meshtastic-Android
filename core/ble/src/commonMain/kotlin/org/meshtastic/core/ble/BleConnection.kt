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

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/** Represents the type of write operation. */
enum class BleWriteType {
    WITH_RESPONSE,
    WITHOUT_RESPONSE,
}

/** Encapsulates a BLE connection to a [BleDevice]. */
interface BleConnection {
    /** The currently connected [BleDevice], or null if not connected. */
    val device: BleDevice?

    /** A flow of the current device. */
    val deviceFlow: SharedFlow<BleDevice?>

    /** A flow of [BleConnectionState] changes. */
    val connectionState: SharedFlow<BleConnectionState>

    /** Connects to the given [BleDevice]. */
    suspend fun connect(device: BleDevice)

    /** Connects to the given [BleDevice] and waits for a terminal state. */
    suspend fun connectAndAwait(device: BleDevice, timeoutMs: Long): BleConnectionState

    /** Disconnects from the current device. */
    suspend fun disconnect()

    /** Executes a block within a discovered profile. */
    suspend fun <T> profile(
        serviceUuid: Uuid,
        timeout: Duration = 30.seconds,
        setup: suspend CoroutineScope.(BleService) -> T,
    ): T

    /** Returns the maximum write value length for the given write type. */
    fun maximumWriteValueLength(writeType: BleWriteType): Int?
}

/** Represents a BLE service for commonMain. */
interface BleService {
    // This will be expanded as needed, but for now we just need a common type to pass around.
}
