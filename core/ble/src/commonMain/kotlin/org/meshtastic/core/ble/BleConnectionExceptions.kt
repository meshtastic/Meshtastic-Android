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

import org.meshtastic.core.model.RadioNotConnectedException

/** A scan for the selected address saw no advertisement. */
class BleDeviceNotFoundException(message: String) : RadioNotConnectedException(message)

/** The link did not come up; [cause] carries the platform's rejection when it gave one. */
class BleConnectFailedException(message: String, cause: Throwable? = null) : RadioNotConnectedException(message, cause)

/** Bonding failed and the device is still not bonded. */
class BlePairingFailedException(cause: Throwable) :
    RadioNotConnectedException("Bonding failed and device is still not bonded", cause)
