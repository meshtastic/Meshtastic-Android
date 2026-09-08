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

/**
 * Lockdown session state for the connected device.
 *
 * [None] means the current connection has no concrete runtime lockdown state. This is expected for pre-2.8 firmware,
 * which does not implement the runtime lockdown handshake, and for newer builds that do not include runtime lockdown
 * support. [allowsConfigWrites] summarizes only whether lockdown state withholds normal admin configuration writes;
 * managed-device and other client policy remain separate.
 */
sealed class LockdownState {
    data object None : LockdownState()

    /**
     * A manual or automatic passphrase command was admitted to the active transport and is waiting for the firmware's
     * next lockdown status.
     *
     * Write eligibility remains unresolved until that response arrives. A rejected dispatch leaves the previous
     * retryable state in place instead of entering this state.
     */
    data object AwaitingResponse : LockdownState()

    /**
     * Device is locked or this client is not yet authorized.
     *
     * @param lockReason machine-readable reason from firmware (e.g. "needs_auth", "token_missing", "token_expired").
     *   Empty string when unknown.
     */
    data class Locked(val lockReason: String = "") : LockdownState()

    data object NeedsProvision : LockdownState()

    data object Unlocked : LockdownState()

    /** Lockdown-capable firmware explicitly reported that lockdown is disabled. */
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

    /**
     * True when the current lockdown state does not withhold normal configuration writes.
     *
     * No received lockdown status ([None]), explicitly disabled lockdown ([Disabled]), and an authenticated lockdown
     * session ([Unlocked]) do not withhold writes on lockdown grounds. [AwaitingResponse] keeps write eligibility
     * unresolved until firmware reports the next state; locked, provisioning, and authentication-failure states
     * withhold admin access. Managed-device policy is intentionally evaluated separately by the presentation.
     */
    val allowsConfigWrites: Boolean
        get() =
            when (this) {
                is None,
                is Disabled,
                is Unlocked,
                -> true

                is AwaitingResponse,
                is Locked,
                is NeedsProvision,
                is LockNowAcknowledged,
                is UnlockFailed,
                is UnlockBackoff,
                -> false
            }
}

/**
 * Lockdown session token metadata from a successful unlock.
 *
 * @param bootsRemaining Number of reboots before the token expires.
 * @param expiryEpoch Unix epoch seconds; 0 means no time-based expiry.
 */
data class LockdownTokenInfo(val bootsRemaining: Int, val expiryEpoch: Long)
