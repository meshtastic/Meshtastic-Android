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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.Uuid

/** Represents the type of write operation. */
enum class BleWriteType {
    WITH_RESPONSE,
    WITHOUT_RESPONSE,
}

/** Identifies a characteristic within a profiled BLE service. */
data class BleCharacteristic(val uuid: Uuid)

/** Safe ATT payload length when MTU negotiation is unavailable (23-byte ATT MTU minus 3-byte header). */
const val DEFAULT_BLE_WRITE_VALUE_LENGTH = 20

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

    /** Returns the maximum write value length for the given write type, or `null` if unknown. */
    fun maximumWriteValueLength(writeType: BleWriteType): Int?
}

/** Represents a BLE service for commonMain. */
interface BleService {
    /** Creates a handle for a characteristic belonging to this service. */
    fun characteristic(uuid: Uuid): BleCharacteristic = BleCharacteristic(uuid)

    /** Returns true when the characteristic is present on the connected device. */
    fun hasCharacteristic(characteristic: BleCharacteristic): Boolean

    /** Observes notifications/indications from the characteristic. */
    fun observe(characteristic: BleCharacteristic): Flow<ByteArray>

    /** Reads the characteristic value once. */
    suspend fun read(characteristic: BleCharacteristic): ByteArray

    /** Returns the preferred write type for the characteristic on this platform/device. */
    fun preferredWriteType(characteristic: BleCharacteristic): BleWriteType

    /** Writes a value to the characteristic using the requested BLE write type. */
    suspend fun write(characteristic: BleCharacteristic, data: ByteArray, writeType: BleWriteType)
}
