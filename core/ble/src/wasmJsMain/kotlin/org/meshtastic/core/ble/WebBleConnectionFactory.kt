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

/**
 * [BleConnectionFactory] for the Web Bluetooth-backed [WebBleConnection].
 *
 * Unlike `KableBleConnectionFactory` (`nonWebMain`), this takes no `BleLoggingConfig` — that type (and the Kable
 * logging it configures) lives entirely in `nonWebMain` and has no web equivalent to wire up.
 */
@Single
class WebBleConnectionFactory : BleConnectionFactory {
    /** [tag] is unused: [WebBleConnection] has no per-peripheral log identifier to attach it to. */
    override fun create(scope: CoroutineScope, tag: String): BleConnection = WebBleConnection(scope)
}
