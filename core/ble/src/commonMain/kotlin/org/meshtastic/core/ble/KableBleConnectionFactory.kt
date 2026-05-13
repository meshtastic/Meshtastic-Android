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

import kotlinx.coroutines.CoroutineScope
import org.koin.core.annotation.Single

@Single
class KableBleConnectionFactory(private val loggingConfig: BleLoggingConfig) : BleConnectionFactory {
    /**
     * Creates a new [KableBleConnection].
     *
     * [tag] is unused because Kable's own log identifier is set per-peripheral inside [KableBleConnection.connect]
     * using the device address, which provides more precise context than a factory-time tag.
     */
    override fun create(scope: CoroutineScope, tag: String): BleConnection = KableBleConnection(scope, loggingConfig)
}
