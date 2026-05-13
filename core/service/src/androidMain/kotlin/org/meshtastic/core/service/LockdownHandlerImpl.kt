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
package org.meshtastic.core.service

import co.touchlab.kermit.Logger
import org.koin.core.annotation.Single
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.meshtastic.core.model.service.LockdownState
import org.meshtastic.core.model.service.LockdownTokenInfo
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.LockdownCoordinator
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.LockdownStatus

@Single(binds = [LockdownCoordinator::class])
class LockdownHandlerImpl(
    private val serviceRepository: ServiceRepository,
    private val commandSender: CommandSender,
    private val passphraseStore: LockdownPassphraseStore,
    private val radioInterfaceService: RadioInterfaceService,
) : LockdownCoordinator, KoinComponent {
    private val connectionManager: MeshConnectionManager by inject()
    @Volatile private var wasAutoAttempt = false

    @Volatile private var wasLockNow = false

    @Volatile private var pendingPassphrase: String? = null

    @Volatile private var pendingBoots: Int = LockdownPassphraseStore.DEFAULT_BOOTS

    @Volatile private var pendingHours: Int = 0

    /** Called when the BLE connection is established, before the first config request. */
    override fun onConnect() {
        serviceRepository.setSessionAuthorized(false)
        wasAutoAttempt = false
        wasLockNow = false
        pendingPassphrase = null
        pendingBoots = LockdownPassphraseStore.DEFAULT_BOOTS
        pendingHours = 0
    }

    /** Called when the BLE connection is lost. */
    override fun onDisconnect() {
        serviceRepository.setSessionAuthorized(false)
        serviceRepository.setLockdownTokenInfo(null)
        serviceRepository.setLockdownState(LockdownState.None)
        wasAutoAttempt = false
        wasLockNow = false
        pendingPassphrase = null
    }

    /**
     * Called on every config_complete_id. Once [sessionAuthorized] is true (set on UNLOCKED),
     * this is a no-op — preventing the startConfigOnly config_complete_id from triggering any
     * further lockdown handling.
     */
    override fun onConfigComplete() {
        if (serviceRepository.sessionAuthorized.value) return
    }

    /** Routes typed firmware [LockdownStatus] to per-state handlers. */
    override fun handleLockdownStatus(status: LockdownStatus) {
        when (status.state) {
            LockdownStatus.State.NEEDS_PROVISION -> handleNeedsProvision()
            LockdownStatus.State.LOCKED -> handleLocked(status.lock_reason)
            LockdownStatus.State.UNLOCKED -> handleUnlocked(status)
            LockdownStatus.State.UNLOCK_FAILED -> handleUnlockFailed(status.backoff_seconds)
            LockdownStatus.State.STATE_UNSPECIFIED -> Unit
        }
    }

    private fun handleLockNowAcknowledged() {
        Logger.i { "Lockdown: Lock Now acknowledged — resetting session authorization" }
        serviceRepository.setSessionAuthorized(false)
        wasAutoAttempt = false
        wasLockNow = false
        pendingPassphrase = null
        // Purge cached config; fresh config is loaded after successful re-authentication.
        connectionManager.clearRadioConfig()
        // Signal the UI to disconnect — no dialog, just drop the connection.
        serviceRepository.setLockdownState(LockdownState.LockNowAcknowledged)
    }

    private fun handleLocked(lockReason: String) {
        if (wasLockNow) {
            handleLockNowAcknowledged()
            return
        }
        val deviceAddress = radioInterfaceService.getDeviceAddress()
        if (deviceAddress != null) {
            val stored = passphraseStore.getPassphrase(deviceAddress)
            if (stored != null) {
                Logger.i { "Lockdown: Auto-unlocking (reason=$lockReason) with stored passphrase for $deviceAddress" }
                wasAutoAttempt = true
                commandSender.sendLockdownPassphrase(stored.passphrase, stored.boots, stored.hours)
                return
            }
        }
        serviceRepository.setLockdownState(LockdownState.Locked(lockReason))
    }

    private fun handleNeedsProvision() {
        serviceRepository.setLockdownState(LockdownState.NeedsProvision)
    }

    private fun handleUnlocked(status: LockdownStatus) {
        val deviceAddress = radioInterfaceService.getDeviceAddress()
        val passphrase = pendingPassphrase
        if (deviceAddress != null && passphrase != null) {
            passphraseStore.savePassphrase(deviceAddress, passphrase, pendingBoots, pendingHours)
            Logger.i { "Lockdown: Saved passphrase for $deviceAddress" }
        }
        pendingPassphrase = null
        serviceRepository.setLockdownTokenInfo(
            LockdownTokenInfo(
                bootsRemaining = status.boots_remaining,
                expiryEpoch = status.valid_until_epoch.toLong() and UINT32_MASK,
            ),
        )
        serviceRepository.setLockdownState(LockdownState.Unlocked)
        // Mark session authorized BEFORE calling startConfigOnly(). When the resulting
        // config_complete_id arrives, onConfigComplete() will see sessionAuthorized=true and
        // return immediately — no passphrase re-send, no loop.
        serviceRepository.setSessionAuthorized(true)
        connectionManager.startConfigOnly()
    }

    private fun handleUnlockFailed(backoffSeconds: Int) {
        pendingPassphrase = null
        if (wasAutoAttempt) {
            wasAutoAttempt = false
            if (backoffSeconds > 0) {
                Logger.i { "Lockdown: Auto-unlock rate-limited (backoff=${backoffSeconds}s)" }
                serviceRepository.setLockdownState(LockdownState.UnlockBackoff(backoffSeconds))
            } else {
                val deviceAddress = radioInterfaceService.getDeviceAddress()
                if (deviceAddress != null) {
                    passphraseStore.clearPassphrase(deviceAddress)
                    Logger.i { "Lockdown: Auto-unlock failed (wrong passphrase), cleared stored passphrase for $deviceAddress" }
                }
                serviceRepository.setLockdownState(LockdownState.Locked())
            }
            return
        }
        if (backoffSeconds > 0) {
            Logger.i { "Lockdown: Unlock failed with backoff of ${backoffSeconds}s" }
            serviceRepository.setLockdownState(LockdownState.UnlockBackoff(backoffSeconds))
        } else {
            serviceRepository.setLockdownState(LockdownState.UnlockFailed)
        }
    }

    override fun submitPassphrase(passphrase: String, boots: Int, hours: Int) {
        pendingPassphrase = passphrase
        pendingBoots = boots
        pendingHours = hours
        wasAutoAttempt = false
        wasLockNow = false
        serviceRepository.setLockdownState(LockdownState.None) // hide dialog while awaiting response
        commandSender.sendLockdownPassphrase(passphrase, boots, hours)
    }

    override fun lockNow() {
        wasLockNow = true
        commandSender.sendLockNow()
    }

    companion object {
        private const val UINT32_MASK = 0xFFFFFFFFL
    }
}
