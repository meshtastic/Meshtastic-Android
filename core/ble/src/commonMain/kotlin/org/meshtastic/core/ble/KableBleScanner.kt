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

import com.juul.kable.Scanner
import kotlinx.coroutines.flow.Flow
import org.koin.core.annotation.Single
import kotlin.time.Duration
import kotlin.uuid.Uuid

@Single
class KableBleScanner : BleScanner {
    override fun scan(timeout: Duration, serviceUuid: Uuid?, address: String?): Flow<BleDevice> {
        val scanner = Scanner {
            // When both serviceUuid and address are provided (the findDevice reconnect path),
            // filter by service UUID only. The caller applies address filtering post-collection.
            // Using a single match{} with both creates an AND filter that silently drops results
            // on some OEM BLE stacks (Samsung, Xiaomi) when the device uses a random resolvable
            // private address. Using separate match{} blocks creates OR semantics which would
            // return all Meshtastic devices, so we only filter by service UUID in that case.
            if (serviceUuid != null || address != null) {
                filters {
                    if (serviceUuid != null) {
                        match { services = listOf(serviceUuid) }
                    } else if (address != null) {
                        // Address-only scan (no service UUID filter). BLE MAC addresses are
                        // normalized to uppercase on Android; uppercase() covers any edge cases.
                        match { this.address = address.uppercase() }
                    }
                }
            }
        }

        // Kable's Scanner doesn't enforce timeout internally, it runs until the Flow is cancelled.
        // By wrapping it in a channelFlow with a timeout, we enforce the BleScanner contract cleanly.
        return kotlinx.coroutines.flow.channelFlow {
            kotlinx.coroutines.withTimeoutOrNull(timeout) {
                scanner.advertisements.collect { advertisement -> send(KableBleDevice(advertisement)) }
            }
        }
    }
}
