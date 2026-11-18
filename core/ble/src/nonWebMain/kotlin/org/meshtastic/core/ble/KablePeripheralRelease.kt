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
package org.meshtastic.core.ble

import com.juul.kable.ExperimentalKableApi
import com.juul.kable.Peripheral

// Each connect builds its own peripheral here, so release closes it: Kable needs close() to drop Android's broadcast
// receivers (Kable issue #359).
internal actual fun Peripheral.release() = close()

@OptIn(ExperimentalKableApi::class)
internal actual suspend fun Peripheral.connectedRssi(): Int? = rssi()

// Each connect builds its peripheral, and the observation handler with it, inside KableBleConnection.
internal actual fun Peripheral.preinstalledObservationFailure(): Throwable? = null

internal actual fun Peripheral.clearPreinstalledObservationFailure() = Unit
