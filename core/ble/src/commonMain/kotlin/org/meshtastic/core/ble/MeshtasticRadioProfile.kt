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

import kotlinx.coroutines.flow.Flow

/** A definition of the Meshtastic BLE Service profile. */
interface MeshtasticRadioProfile {
    /** The flow of incoming packets from the radio. */
    val fromRadio: Flow<ByteArray>

    /** The flow of incoming log packets from the radio. */
    val logRadio: Flow<ByteArray>

    /** Sends a packet to the radio. */
    suspend fun sendToRadio(packet: ByteArray)
}
