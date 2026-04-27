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
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.annotation.Single
import kotlin.time.Duration
import kotlin.uuid.Uuid

@Single
class KableBleScanner(private val loggingConfig: BleLoggingConfig) : BleScanner {
    override fun scan(timeout: Duration, serviceUuid: Uuid?, address: String?): Flow<BleDevice> {
        val scanner = Scanner {
            logging { applyConfig(loggingConfig) }
            // Use separate match blocks so each filter is evaluated independently (OR semantics).
            // Combining address and service UUID in a single match{} creates an AND filter which
            // silently drops results on OEM stacks (Samsung, Xiaomi) when the device uses a
            // random resolvable private address.
            if (address != null) {
                filters { match { this.address = address } }
            } else if (serviceUuid != null) {
                filters { match { services = listOf(serviceUuid) } }
            }
        }

        // Kable's Scanner doesn't enforce timeout internally, it runs until the Flow is cancelled.
        // By wrapping it in a channelFlow with a timeout, we enforce the BleScanner contract cleanly.
        return channelFlow {
            withTimeoutOrNull(timeout) {
                scanner.advertisements.collect { advertisement ->
                    send(
                        MeshtasticBleDevice(
                            address = advertisement.identifier.toString(),
                            name = advertisement.name,
                            advertisement = advertisement,
                        ),
                    )
                }
            }
        }
    }
}
