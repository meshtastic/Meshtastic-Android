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
package org.meshtastic.core.repository

import org.meshtastic.proto.LockdownStatus

/**
 * Coordinates lockdown (TAK passphrase) authentication for TAK-locked devices.
 *
 * Implementations handle the full authentication lifecycle: auto-unlock with a stored
 * passphrase, manual passphrase submission, lock-now, and session lifecycle hooks.
 */
interface LockdownCoordinator {
    /** Called when a BLE connection is established, before the first config request. */
    fun onConnect()

    /** Called when a BLE connection is lost. */
    fun onDisconnect()

    /**
     * Called on every config_complete_id from the device.
     * After session is authorized this is a no-op to prevent re-triggering lockdown logic.
     */
    fun onConfigComplete()

    /** Routes an incoming typed [LockdownStatus] from FromRadio. */
    fun handleLockdownStatus(status: LockdownStatus)

    /** Submits a passphrase to authenticate with the locked device. */
    fun submitPassphrase(passphrase: String, boots: Int, hours: Int)

    /** Sends a Lock Now command to the connected device. */
    fun lockNow()
}
