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
package org.meshtastic.core.data.manager

import co.touchlab.kermit.Logger
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
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

/**
 * Lockdown authentication state machine. Processes `LockdownStatus` messages from the firmware, drives the
 * `LockdownState` exposed to the UI, and manages auto-replay of cached passphrases.
 *
 * The UI and the session receiver serialize commands and replies so a fast firmware reply cannot arrive before the
 * command's pending state is installed.
 */
@Single(binds = [LockdownCoordinator::class])
@Suppress("TooManyFunctions")
class LockdownCoordinatorImpl(
    private val serviceRepository: ServiceRepository,
    private val commandSender: CommandSender,
    private val passphraseStore: LockdownPassphraseStore,
    private val radioInterfaceService: RadioInterfaceService,
    private val connectionManager: Lazy<MeshConnectionManager>,
) : LockdownCoordinator {
    private val lock = SynchronizedObject()

    private var wasAutoAttempt = false

    private var wasLockNow = false

    private var pendingPassphrase: String? = null

    private var pendingBoots: Int = LockdownPassphraseStore.DEFAULT_BOOTS

    private var pendingHours: Int = 0

    private var pendingMaxSessionSeconds: Int = 0

    override fun onConnect() = synchronized(lock) {
        serviceRepository.setSessionAuthorized(false)
        resetTransientState()
    }

    override fun onDisconnect() = synchronized(lock) {
        serviceRepository.setSessionAuthorized(false)
        serviceRepository.setLockdownTokenInfo(null)
        serviceRepository.setLockdownState(LockdownState.None)
        resetTransientState()
    }

    override fun handleLockdownStatus(status: LockdownStatus) = synchronized(lock) {
        when (status.state) {
            LockdownStatus.State.NEEDS_PROVISION -> handleNeedsProvision()

            LockdownStatus.State.LOCKED -> handleLocked(status.lock_reason)

            LockdownStatus.State.UNLOCKED -> handleUnlocked(status)

            LockdownStatus.State.UNLOCK_FAILED -> handleUnlockFailed(status.backoff_seconds)

            LockdownStatus.State.DISABLED -> handleDisabled()

            LockdownStatus.State.STATE_UNSPECIFIED ->
                Logger.w { "Lockdown: Received STATE_UNSPECIFIED from firmware" }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun handleDisabled() {
        // Lockdown-capable but currently OFF. Drop any stale stored passphrase so we don't try to auto-unlock later.
        val deviceAddress = radioInterfaceService.getDeviceAddress()
        if (deviceAddress != null) {
            try {
                passphraseStore.clearPassphrase(deviceAddress)
            } catch (e: Exception) {
                Logger.e(e) { "Lockdown: Failed to clear stored passphrase on DISABLED" }
            }
        }
        resetTransientState()
        serviceRepository.setSessionAuthorized(false)
        serviceRepository.setLockdownTokenInfo(null)
        serviceRepository.setLockdownState(LockdownState.Disabled)
    }

    private fun handleLockNowAcknowledged() {
        Logger.i { "Lockdown: Lock Now acknowledged — resetting session authorization" }
        serviceRepository.setSessionAuthorized(false)
        resetTransientState()
        connectionManager.value.clearRadioConfig()
        serviceRepository.setLockdownState(LockdownState.LockNowAcknowledged)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun handleLocked(lockReason: String) {
        if (wasLockNow) {
            handleLockNowAcknowledged()
            return
        }
        val deviceAddress = radioInterfaceService.getDeviceAddress()
        if (deviceAddress != null) {
            val stored =
                try {
                    passphraseStore.getPassphrase(deviceAddress)
                } catch (e: Exception) {
                    Logger.e(e) { "Lockdown: Failed to read stored passphrase" }
                    null
                }
            if (stored != null) {
                Logger.i { "Lockdown: Auto-unlocking with stored passphrase" }
                // A fresh LOCKED status ends any previous auto-attempt outcome. Only an admitted replay arms failure
                // handling for the new attempt.
                wasAutoAttempt = false
                val dispatched =
                    commandSender.sendLockdownPassphrase(
                        stored.passphrase,
                        stored.boots,
                        stored.hours,
                        stored.maxSessionSeconds,
                    )
                if (dispatched) {
                    wasAutoAttempt = true
                    serviceRepository.setLockdownState(LockdownState.AwaitingResponse)
                    return
                }
                Logger.w { "Lockdown: Auto-unlock command was not accepted by the active transport" }
            }
        }
        serviceRepository.setLockdownState(LockdownState.Locked(lockReason))
    }

    private fun handleNeedsProvision() {
        serviceRepository.setLockdownState(LockdownState.NeedsProvision)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun handleUnlocked(status: LockdownStatus) {
        val deviceAddress = radioInterfaceService.getDeviceAddress()
        val passphrase = pendingPassphrase
        // Only save on manual submit — auto-unlock already has a stored passphrase.
        if (deviceAddress != null && passphrase != null) {
            try {
                passphraseStore.savePassphrase(
                    deviceAddress,
                    passphrase,
                    pendingBoots,
                    pendingHours,
                    pendingMaxSessionSeconds,
                )
                Logger.i { "Lockdown: Saved passphrase for device" }
            } catch (e: Exception) {
                Logger.e(e) { "Lockdown: Failed to persist passphrase (session still unlocked)" }
            }
        }
        pendingPassphrase = null
        serviceRepository.setLockdownTokenInfo(
            LockdownTokenInfo(
                bootsRemaining = status.boots_remaining,
                expiryEpoch = status.valid_until_epoch.toUInt().toLong(),
            ),
        )
        serviceRepository.setLockdownState(LockdownState.Unlocked)
        serviceRepository.setSessionAuthorized(true)
    }

    @Suppress("TooGenericExceptionCaught")
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
                    try {
                        passphraseStore.clearPassphrase(deviceAddress)
                        Logger.i { "Lockdown: Auto-unlock failed, cleared stored passphrase" }
                    } catch (e: Exception) {
                        Logger.e(e) { "Lockdown: Auto-unlock failed AND could not clear stored passphrase" }
                    }
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

    @Suppress("TooGenericExceptionCaught")
    override fun submitPassphrase(
        passphrase: String,
        boots: Int,
        hours: Int,
        maxSessionSeconds: Int,
        disable: Boolean,
    ): Boolean = synchronized(lock) {
        if (!commandSender.sendLockdownPassphrase(passphrase, boots, hours, maxSessionSeconds, disable)) {
            Logger.w { "Lockdown: Passphrase command was not accepted by the active transport" }
            return@synchronized false
        }

        wasAutoAttempt = false
        wasLockNow = false
        if (disable) {
            // Turning lockdown OFF: the device will reboot to DISABLED, so there is nothing to re-save. Clear the
            // stored passphrase only after transport admission succeeds so a rejected request remains retryable.
            pendingPassphrase = null
            pendingBoots = LockdownPassphraseStore.DEFAULT_BOOTS
            pendingHours = 0
            pendingMaxSessionSeconds = 0
            val deviceAddress = radioInterfaceService.getDeviceAddress()
            if (deviceAddress != null) {
                try {
                    passphraseStore.clearPassphrase(deviceAddress)
                } catch (e: Exception) {
                    Logger.e(e) { "Lockdown: Failed to clear stored passphrase while disabling" }
                }
            }
        } else {
            pendingPassphrase = passphrase
            pendingBoots = boots
            pendingHours = hours
            pendingMaxSessionSeconds = maxSessionSeconds
        }
        serviceRepository.setLockdownState(LockdownState.AwaitingResponse)
        true
    }

    override fun lockNow(): Boolean = synchronized(lock) {
        val dispatched = commandSender.sendLockNow()
        if (dispatched) {
            wasLockNow = true
        } else {
            Logger.w { "Lockdown: Lock Now command was not accepted by the active transport" }
        }
        dispatched
    }

    private fun resetTransientState() {
        wasAutoAttempt = false
        wasLockNow = false
        pendingPassphrase = null
        pendingBoots = LockdownPassphraseStore.DEFAULT_BOOTS
        pendingHours = 0
        pendingMaxSessionSeconds = 0
    }
}
