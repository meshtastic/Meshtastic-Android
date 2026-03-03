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
package com.geeksville.mesh.service

import co.touchlab.kermit.Logger
import com.geeksville.mesh.repository.radio.RadioInterfaceService
import dagger.Lazy
import org.meshtastic.core.service.LockdownState
import org.meshtastic.core.service.LockdownTokenInfo
import org.meshtastic.core.service.ServiceRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LockdownHandler @Inject constructor(
    private val serviceRepository: ServiceRepository,
    private val commandSender: MeshCommandSender,
    private val passphraseStore: LockdownPassphraseStore,
    private val radioInterfaceService: RadioInterfaceService,
    private val connectionManager: Lazy<MeshConnectionManager>,
) {
    @Volatile private var wasAutoAttempt = false

    @Volatile private var pendingPassphrase: String? = null
    @Volatile private var pendingBoots: Int = LockdownPassphraseStore.DEFAULT_BOOTS
    @Volatile private var pendingHours: Int = 0

    /** Called when the BLE connection is established, before the first config request. */
    fun onConnect() {
        serviceRepository.setSessionAuthorized(false)
        wasAutoAttempt = false
        pendingPassphrase = null
        pendingBoots = LockdownPassphraseStore.DEFAULT_BOOTS
        pendingHours = 0
    }

    /** Called when the BLE connection is lost. */
    fun onDisconnect() {
        serviceRepository.setSessionAuthorized(false)
        serviceRepository.setLockdownTokenInfo(null)
        serviceRepository.setLockdownState(LockdownState.None)
        wasAutoAttempt = false
        pendingPassphrase = null
    }

    /**
     * Called on every config_complete_id. Once [sessionAuthorized] is true (set on TAK_UNLOCKED),
     * this is a no-op — preventing the startConfigOnly config_complete_id from triggering any
     * further lockdown handling. The dialog state is driven entirely by clientNotifications.
     */
    fun onConfigComplete() {
        // Session already authenticated — this config_complete_id is from the startConfigOnly()
        // issued after TAK_UNLOCKED. Nothing to do.
        if (serviceRepository.sessionAuthorized.value) return
    }

    /**
     * Routes incoming lockdown clientNotification messages:
     *  - TAK_NEEDS_PROVISION → device has no passphrase → show "Set Passphrase" dialog
     *  - TAK_LOCKED:<reason> → device is locked → auto-unlock with stored passphrase or show dialog
     *  - TAK_UNLOCKED        → accepted; save passphrase, authorize session, re-sync config
     *  - TAK_UNLOCK_FAILED   → wrong passphrase; clear stored or increment retry counter
     */
    fun handleLockdownNotification(message: String?) {
        when {
            message == TAK_NEEDS_PROVISION -> handleNeedsProvision()
            // Exact "TAK_LOCKED" = Lock Now was acknowledged by the device → re-lock the session.
            // "TAK_LOCKED:<reason>" (with colon) = connect-time lock → try auto-unlock or show dialog.
            message == TAK_LOCKED_ACK -> handleLockNowAcknowledged()
            message != null && message.startsWith(TAK_LOCKED_WITH_REASON_PREFIX) -> handleLocked()
            message != null && message.startsWith(TAK_UNLOCKED_PREFIX) -> handleUnlocked(message)
            message != null && message.startsWith(TAK_UNLOCK_FAILED_PREFIX) -> handleUnlockFailed(message)
        }
    }

    private fun handleLockNowAcknowledged() {
        Logger.i { "Lockdown: Lock Now acknowledged — resetting session authorization" }
        serviceRepository.setSessionAuthorized(false)
        // Do NOT clear lockdownTokenInfo here — keep it so the dialog pre-fills with the last-known
        // TTL values. It is refreshed by the next TAK_UNLOCKED response.
        wasAutoAttempt = false
        pendingPassphrase = null
        // Immediately purge the cached config — it's stale from the authenticated session.
        // The fresh config is loaded in handleUnlocked() after successful re-authentication.
        connectionManager.get().clearRadioConfig()
        // Signal the UI to disconnect — no dialog, just drop the connection.
        serviceRepository.setLockdownState(LockdownState.LockNowAcknowledged)
    }

    private fun handleLocked() {
        val deviceAddress = radioInterfaceService.getDeviceAddress()
        if (deviceAddress != null) {
            val stored = passphraseStore.getPassphrase(deviceAddress)
            if (stored != null) {
                Logger.i { "Lockdown: Auto-unlocking (TAK_LOCKED) with stored passphrase for $deviceAddress" }
                wasAutoAttempt = true
                commandSender.sendLockdownPassphrase(stored.passphrase, stored.boots, stored.hours)
                return
            }
        }
        serviceRepository.setLockdownState(LockdownState.Locked)
    }

    private fun handleNeedsProvision() {
        serviceRepository.setLockdownState(LockdownState.NeedsProvision)
    }

    private fun handleUnlocked(message: String) {
        val deviceAddress = radioInterfaceService.getDeviceAddress()
        val passphrase = pendingPassphrase
        if (deviceAddress != null && passphrase != null) {
            passphraseStore.savePassphrase(deviceAddress, passphrase, pendingBoots, pendingHours)
            Logger.i { "Lockdown: Saved passphrase for $deviceAddress" }
        }
        pendingPassphrase = null
        serviceRepository.setLockdownTokenInfo(parseTokenInfo(message))
        serviceRepository.setLockdownState(LockdownState.Unlocked)
        // Mark session authorized BEFORE calling startConfigOnly(). When the resulting
        // config_complete_id arrives, onConfigComplete() will see sessionAuthorized=true and return
        // immediately — no passphrase re-send, no loop.
        serviceRepository.setSessionAuthorized(true)
        connectionManager.get().startConfigOnly()
    }

    /** Parses boots= and until= fields from TAK_UNLOCKED:boots=N:until=EPOCH: */
    private fun parseTokenInfo(message: String): LockdownTokenInfo? {
        var boots = -1
        var until = 0L
        for (segment in message.split(":")) {
            when {
                segment.startsWith("boots=") -> boots = segment.removePrefix("boots=").toIntOrNull() ?: -1
                segment.startsWith("until=") -> until = segment.removePrefix("until=").toLongOrNull() ?: 0L
            }
        }
        return if (boots >= 0) LockdownTokenInfo(boots, until) else null
    }

    private fun handleUnlockFailed(message: String) {
        pendingPassphrase = null
        // Parse backoff=N first — applies to both auto and manual attempts.
        val backoffSeconds = message.split(":").firstNotNullOfOrNull { segment ->
            if (segment.startsWith("backoff=")) segment.removePrefix("backoff=").toIntOrNull() else null
        }
        if (wasAutoAttempt) {
            wasAutoAttempt = false
            if (backoffSeconds != null && backoffSeconds > 0) {
                // Rate-limited — stored passphrase may still be correct; keep it and show countdown.
                Logger.i { "Lockdown: Auto-unlock rate-limited (backoff=${backoffSeconds}s)" }
                serviceRepository.setLockdownState(LockdownState.UnlockBackoff(backoffSeconds))
            } else {
                // Wrong passphrase — clear stored passphrase.
                val deviceAddress = radioInterfaceService.getDeviceAddress()
                if (deviceAddress != null) {
                    passphraseStore.clearPassphrase(deviceAddress)
                    Logger.i { "Lockdown: Auto-unlock failed (wrong passphrase), cleared stored passphrase for $deviceAddress" }
                }
                serviceRepository.setLockdownState(LockdownState.Locked)
            }
            return
        }
        // Manual attempt.
        if (backoffSeconds != null && backoffSeconds > 0) {
            Logger.i { "Lockdown: Unlock failed with backoff of ${backoffSeconds}s" }
            serviceRepository.setLockdownState(LockdownState.UnlockBackoff(backoffSeconds))
        } else {
            serviceRepository.setLockdownState(LockdownState.UnlockFailed)
        }
    }

    fun submitPassphrase(passphrase: String, boots: Int, hours: Int) {
        pendingPassphrase = passphrase
        pendingBoots = boots
        pendingHours = hours
        wasAutoAttempt = false
        serviceRepository.setLockdownState(LockdownState.None) // hide dialog while awaiting response
        commandSender.sendLockdownPassphrase(passphrase, boots, hours)
    }

    fun lockNow() {
        commandSender.sendLockNow()
    }

    companion object {
        private const val TAK_LOCKED_ACK = "TAK_LOCKED"           // exact: Lock Now ACK
        private const val TAK_LOCKED_WITH_REASON_PREFIX = "TAK_LOCKED:" // connect-time lock
        private const val TAK_NEEDS_PROVISION = "TAK_NEEDS_PROVISION"
        private const val TAK_UNLOCKED_PREFIX = "TAK_UNLOCKED"
        private const val TAK_UNLOCK_FAILED_PREFIX = "TAK_UNLOCK_FAILED"
    }
}
