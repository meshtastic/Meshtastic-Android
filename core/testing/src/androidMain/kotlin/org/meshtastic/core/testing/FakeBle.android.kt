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
package org.meshtastic.core.testing

/**
 * Android-specific override: throw [SecurityException] (the missing-permission path on Android). This is needed because
 * [SecurityException] is JVM-only and not available in commonMain.
 */
fun FakeBluetoothRepository.failBondWithSecurityException(message: String = "BLUETOOTH_CONNECT not granted") {
    bondOutcome = FakeBluetoothRepository.BondOutcome.Security(SecurityException(message))
}
