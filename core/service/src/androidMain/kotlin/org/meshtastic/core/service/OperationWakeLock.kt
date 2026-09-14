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
@file:Suppress("TooGenericExceptionCaught")

package org.meshtastic.core.service

import android.content.Context
import android.os.PowerManager
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The partial wake lock held while a long radio operation — a firmware flash, a discovery sweep — is in flight.
 *
 * A foreground service keeps the process alive but does not keep the CPU awake, so without this a screen-off flash or
 * sweep is left to whatever the OEM's battery optimizations allow. Separate from the mesh service's own connection wake
 * lock because it answers a different question: that one tracks having a device selected, and a firmware update
 * deselects the device precisely so it can flash.
 *
 * Owns its renewal. The platform releases a timed wake lock on expiry and nothing re-emits while the same operation
 * continues, so a USB maintenance sequence waiting on the user's file picker, or a long user-configured sweep, would
 * otherwise lose the CPU partway through.
 */
internal class OperationWakeLock(private val context: Context, private val scope: CoroutineScope) {

    private var wakeLock: PowerManager.WakeLock? = null
    private var renewal: Job? = null

    /** Takes the lock and keeps it re-armed until [stop]. Safe to call while already running. */
    fun start() {
        acquire()
        if (renewal?.isActive == true) return
        renewal =
            scope.launch {
                while (true) {
                    delay(RENEW_INTERVAL_MS)
                    acquire()
                }
            }
    }

    /** Ends the renewal and drops the lock. Safe to call when nothing is held. */
    fun stop() {
        renewal?.cancel()
        renewal = null
        release()
    }

    /**
     * Acquires or re-arms the lock, per the documented pattern: a tag naming this class, an acquire timeout as a safety
     * net, and an explicit release when the work ends.
     *
     * Re-arming an already-held lock is deliberate — it is not reference counted, so a further acquire restarts the
     * timeout rather than nesting.
     */
    private fun acquire() {
        wakeLock?.let { existing ->
            try {
                existing.acquire(TIMEOUT_MS)
            } catch (e: Exception) {
                Logger.w(e) { "Failed to re-arm the radio operation wake lock" }
            }
            return
        }
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val lock =
                powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
                    setReferenceCounted(false)
                }
            lock.acquire(TIMEOUT_MS)
            wakeLock = lock
            Logger.i { "Acquired partial wake lock for a radio operation" }
        } catch (e: SecurityException) {
            Logger.w(e) { "Failed to acquire the radio operation wake lock — WAKE_LOCK permission missing?" }
        } catch (e: Exception) {
            Logger.w(e) { "Failed to acquire the radio operation wake lock" }
        }
    }

    private fun release() {
        val lock = wakeLock ?: return
        try {
            if (lock.isHeld) {
                lock.release()
                Logger.i { "Released the radio operation wake lock" }
            }
        } catch (e: Exception) {
            Logger.w(e) { "Failed to release the radio operation wake lock" }
        } finally {
            wakeLock = null
        }
    }

    private companion object {
        private const val WAKE_LOCK_TAG = "Meshtastic::MeshServiceRadioOperation"

        /** Safety net, not the mechanism that ends the lock: [stop] is what normally releases it. */
        private const val TIMEOUT_MS = 60L * 60L * 1_000L // 1 hour

        /** Comfortably inside [TIMEOUT_MS], so a missed tick cannot strand the lock. */
        private const val RENEW_INTERVAL_MS = 10L * 60L * 1_000L // 10 minutes
    }
}
