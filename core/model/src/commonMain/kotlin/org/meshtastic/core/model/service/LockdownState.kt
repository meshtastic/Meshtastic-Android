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
package org.meshtastic.core.model.service

/** Represents the lockdown authentication state for a firmware-locked device. */
sealed class LockdownState {
    data object None : LockdownState()

    /**
     * Device is locked or this client is not yet authorized.
     *
     * @param lockReason machine-readable reason from firmware (e.g. "needs_auth", "token_missing", "token_expired").
     *   Empty string when unknown.
     */
    data class Locked(val lockReason: String = "") : LockdownState()

    data object NeedsProvision : LockdownState()

    data object Unlocked : LockdownState()

    /** Device is lockdown-capable but lockdown is currently OFF. The toggle shows OFF. */
    data object Disabled : LockdownState()

    /** Lock Now ACK received — client should disconnect immediately, no dialog. */
    data object LockNowAcknowledged : LockdownState()

    /** Wrong passphrase — retry immediately. */
    data object UnlockFailed : LockdownState()

    /** Too many attempts — must wait [backoffSeconds] before retrying. */
    data class UnlockBackoff(val backoffSeconds: Int) : LockdownState() {
        init {
            require(backoffSeconds > 0) { "backoffSeconds must be positive" }
        }
    }
}

/**
 * Lockdown session token metadata from a successful unlock.
 *
 * @param bootsRemaining Number of reboots before the token expires.
 * @param expiryEpoch Unix epoch seconds; 0 means no time-based expiry.
 */
data class LockdownTokenInfo(val bootsRemaining: Int, val expiryEpoch: Long)
