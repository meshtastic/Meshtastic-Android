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
import com.juul.kable.PeripheralBuilder

internal actual suspend fun Peripheral.platformConnectSetup() {
    // Desktop Kable does not support explicit MTU requests or priority requests.
}

internal actual fun PeripheralBuilder.platformConfig() {
    // Desktop Kable uses direct connections without needing autoConnect.
}

internal actual fun createPeripheral(
    address: String,
    builderAction: PeripheralBuilder.() -> Unit
): Peripheral {
    error("Direct connection by address is not currently supported on JVM without an advertisement")
}
