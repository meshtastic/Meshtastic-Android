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

import com.juul.kable.Peripheral
import com.juul.kable.State

/**
 * The peripherals this page holds a Web Bluetooth grant for, by address.
 *
 * A grant comes only from the `requestDevice()` picker (which needs a user gesture) or `getDevices()`, and Kable builds
 * a web [Peripheral] from neither an address nor a closed one, so each granted peripheral is kept for the page's
 * lifetime and reused across reconnects. Wasm runs on one thread, so the plain map needs no lock.
 */
internal object WebBluetoothGrants {
    private val peripherals = mutableMapOf<String, Peripheral>()

    /**
     * Holds [peripheral] for its device and returns it, unless the peripheral already held is connected or connecting:
     * that one is kept and returned instead. A fresh pick otherwise replaces a stale handle, such as one whose grant
     * the browser revoked.
     */
    fun remember(peripheral: Peripheral): Peripheral {
        val held = peripherals[peripheral.identifier]
        return when {
            held === peripheral -> peripheral

            held != null && held.state.value !is State.Disconnected -> held.also { peripheral.close() }

            else -> {
                held?.close()
                peripherals[peripheral.identifier] = peripheral
                peripheral
            }
        }
    }

    fun find(address: String): Peripheral? =
        peripherals.entries.firstOrNull { it.key.equals(address, ignoreCase = true) }?.value

    private val observationFailures = mutableMapOf<String, Throwable>()

    /** Records a notification-subscription failure Kable suppressed on the granted device [address]. */
    fun recordObservationFailure(address: String, cause: Throwable) {
        observationFailures[address.lowercase()] = cause
    }

    fun observationFailure(address: String): Throwable? = observationFailures[address.lowercase()]

    fun clearObservationFailure(address: String) {
        observationFailures.remove(address.lowercase())
    }
}
