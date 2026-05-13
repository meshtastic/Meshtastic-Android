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
package org.meshtastic.core.data.manager

import co.touchlab.kermit.Logger
import org.koin.core.annotation.Single
import org.meshtastic.core.model.service.LockdownState
import org.meshtastic.core.model.service.LockdownTokenInfo
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.LockdownCoordinator
import org.meshtastic.core.repository.LockdownPassphraseStore
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.RadioInterfaceService
import org.meshtastic.core.repository.ServiceRepository
import org.meshtastic.proto.LockdownStatus

@Single(binds = [LockdownCoordinator::class])
class LockdownCoordinatorImpl(
    private val serviceRepository: ServiceRepository,
    private val commandSender: CommandSender,
    private val passphraseStore: LockdownPassphraseStore,
    private val radioInterfaceService: RadioInterfaceService,
    private val connectionManager: MeshConnectionManager,
) : LockdownCoordinator {
    @Volatile private var wasAutoAttempt = false
    @Volatile private var wasLockNow = false
    @Volatile private var pendingPassphrase: String? = null
    @Volatile private var pendingBoots: Int = LockdownPassphraseStore.DEFAULT_BOOTS
    @Volatile private var pendingHours: Int = 0

    override fun onConnect() {
        serviceRepository.setSessionAuthorized(false)
        wasAutoAttempt = false
        wasLockNow = false
        pendingPassphrase = null
        pendingBoots = LockdownPassphraseStore.DEFAULT_BOOTS
        pendingHours = 0
    }

    override fun onDisconnect() {
        serviceRepository.setSessionAuthorized(false)
        serviceRepository.setLockdownTokenInfo(null)
        serviceRepository.setLockdownState(LockdownState.None)
        wasAutoAttempt = false
        wasLockNow = false
        pendingPassphrase = null
    }

    override fun onConfigComplete() {
        if (serviceRepository.sessionAuthorized.value) return
    }

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
        connectionManager.clearRadioConfig()
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
                Logger.i { "Lockdown: Auto-unlocking with stored passphrase" }
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
            Logger.i { "Lockdown: Saved passphrase for device" }
        }
        pendingPassphrase = null
        serviceRepository.setLockdownTokenInfo(
            LockdownTokenInfo(
                bootsRemaining = status.boots_remaining,
                expiryEpoch = status.valid_until_epoch.toLong() and UINT32_MASK,
            ),
        )
        serviceRepository.setLockdownState(LockdownState.Unlocked)
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
                    Logger.i { "Lockdown: Auto-unlock failed, cleared stored passphrase" }
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
        serviceRepository.setLockdownState(LockdownState.None)
        commandSender.sendLockdownPassphrase(passphrase, boots, hours)
    }

    override fun lockNow() {
        wasLockNow = true
        commandSender.sendLockNow()
    }

    private companion object {
        private const val UINT32_MASK = 0xFFFFFFFFL
    }
}
