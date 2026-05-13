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

import org.meshtastic.core.model.DataPacket
import org.meshtastic.core.model.Position
import org.meshtastic.core.model.service.LockdownState
import org.meshtastic.core.repository.CommandSender
import org.meshtastic.core.repository.LockdownPassphraseStore
import org.meshtastic.core.repository.MeshConnectionManager
import org.meshtastic.core.repository.StoredPassphrase
import org.meshtastic.core.testing.FakeRadioInterfaceService
import org.meshtastic.core.testing.FakeServiceRepository
import org.meshtastic.proto.AdminMessage
import org.meshtastic.proto.ChannelSet
import org.meshtastic.proto.LocalConfig
import org.meshtastic.proto.LockdownStatus
import org.meshtastic.proto.Telemetry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Suppress("LargeClass")
class LockdownCoordinatorImplTest {

    // region Fakes

    private class FakePassphraseStore : LockdownPassphraseStore {
        val saved = mutableMapOf<String, StoredPassphrase>()
        var getThrows: Exception? = null
        var saveThrows: Exception? = null
        var clearThrows: Exception? = null

        override fun getPassphrase(deviceAddress: String): StoredPassphrase? {
            getThrows?.let { throw it }
            return saved[deviceAddress]
        }

        override fun savePassphrase(deviceAddress: String, passphrase: String, boots: Int, hours: Int) {
            saveThrows?.let { throw it }
            saved[deviceAddress] = StoredPassphrase(passphrase, boots, hours)
        }

        override fun clearPassphrase(deviceAddress: String) {
            clearThrows?.let { throw it }
            saved.remove(deviceAddress)
        }
    }

    private class FakeCommandSender : CommandSender {
        var lastPassphrase: String? = null
        var lastBoots: Int = 0
        var lastHours: Int = 0
        var lockNowCalled = false

        override fun sendLockdownPassphrase(passphrase: String, boots: Int, hours: Int) {
            lastPassphrase = passphrase
            lastBoots = boots
            lastHours = hours
        }

        override fun sendLockNow() {
            lockNowCalled = true
        }

        // Unused stubs
        override fun getCurrentPacketId(): Long = 0L

        override fun getCachedLocalConfig(): LocalConfig = LocalConfig()

        override fun getCachedChannelSet(): ChannelSet = ChannelSet()

        override fun generatePacketId(): Int = 0

        override fun sendData(p: DataPacket) = Unit

        override fun sendAdmin(destNum: Int, requestId: Int, wantResponse: Boolean, initFn: () -> AdminMessage) = Unit

        override suspend fun sendAdminAwait(
            destNum: Int,
            requestId: Int,
            wantResponse: Boolean,
            initFn: () -> AdminMessage,
        ) = true

        override fun sendPosition(pos: org.meshtastic.proto.Position, destNum: Int?, wantResponse: Boolean) = Unit

        override fun requestPosition(destNum: Int, currentPosition: Position) = Unit

        override fun setFixedPosition(destNum: Int, pos: Position) = Unit

        override fun requestUserInfo(destNum: Int) = Unit

        override fun requestTraceroute(requestId: Int, destNum: Int) = Unit

        override fun requestTelemetry(requestId: Int, destNum: Int, typeValue: Int) = Unit

        override fun requestNeighborInfo(requestId: Int, destNum: Int) = Unit
    }

    private class FakeConnectionManager : MeshConnectionManager {
        var configOnlyCalled = false
        var clearRadioConfigCalled = false

        override fun onRadioConfigLoaded() = Unit

        override fun startConfigOnly() {
            configOnlyCalled = true
        }

        override fun startNodeInfoOnly() = Unit

        override fun onNodeDbReady() = Unit

        override fun updateTelemetry(t: Telemetry) = Unit

        override fun updateStatusNotification(telemetry: Telemetry?) = Unit

        override fun clearRadioConfig() {
            clearRadioConfigCalled = true
        }
    }

    // endregion

    private val serviceRepo = FakeServiceRepository()
    private val commandSender = FakeCommandSender()
    private val passphraseStore = FakePassphraseStore()
    private val radioService = FakeRadioInterfaceService()
    private val connectionManager = FakeConnectionManager()

    private val coordinator =
        LockdownCoordinatorImpl(
            serviceRepository = serviceRepo,
            commandSender = commandSender,
            passphraseStore = passphraseStore,
            radioInterfaceService = radioService,
            connectionManager = lazy { connectionManager },
        )

    private val testDeviceAddress = "AA:BB:CC:DD:EE:FF"

    // region onConnect / onDisconnect

    @Test
    fun `onConnect clears session authorization`() {
        serviceRepo.setSessionAuthorized(true)
        coordinator.onConnect()
        assertEquals(false, serviceRepo.sessionAuthorized.value)
    }

    @Test
    fun `onDisconnect resets all lockdown state`() {
        serviceRepo.setSessionAuthorized(true)
        serviceRepo.setLockdownState(LockdownState.Unlocked)
        coordinator.onDisconnect()
        assertEquals(false, serviceRepo.sessionAuthorized.value)
        assertIs<LockdownState.None>(serviceRepo.lockdownState.value)
        assertNull(serviceRepo.lockdownTokenInfo.value)
    }

    // endregion

    // region NEEDS_PROVISION

    @Test
    fun `NEEDS_PROVISION sets NeedsProvision state`() {
        coordinator.handleLockdownStatus(LockdownStatus(state = LockdownStatus.State.NEEDS_PROVISION))
        assertIs<LockdownState.NeedsProvision>(serviceRepo.lockdownState.value)
    }

    @Test
    fun `STATE_UNSPECIFIED leaves current state unchanged`() {
        serviceRepo.setLockdownState(LockdownState.Locked("needs_auth"))

        coordinator.handleLockdownStatus(LockdownStatus(state = LockdownStatus.State.STATE_UNSPECIFIED))

        val state = serviceRepo.lockdownState.value
        assertIs<LockdownState.Locked>(state)
        assertEquals("needs_auth", state.lockReason)
    }

    // endregion

    // region LOCKED — manual flow

    @Test
    fun `LOCKED with no stored passphrase sets Locked state`() {
        radioService.setDeviceAddress(testDeviceAddress)
        coordinator.handleLockdownStatus(
            LockdownStatus(state = LockdownStatus.State.LOCKED, lock_reason = "needs_auth"),
        )
        val state = serviceRepo.lockdownState.value
        assertIs<LockdownState.Locked>(state)
        assertEquals("needs_auth", state.lockReason)
    }

    @Test
    fun `LOCKED with no device address sets Locked state`() {
        radioService.setDeviceAddress(null)
        coordinator.handleLockdownStatus(LockdownStatus(state = LockdownStatus.State.LOCKED))
        assertIs<LockdownState.Locked>(serviceRepo.lockdownState.value)
    }

    // endregion

    // region LOCKED — auto-replay

    @Test
    fun `LOCKED with stored passphrase triggers auto-unlock`() {
        radioService.setDeviceAddress(testDeviceAddress)
        passphraseStore.saved[testDeviceAddress] = StoredPassphrase("secret", 10, 24)

        coordinator.handleLockdownStatus(
            LockdownStatus(state = LockdownStatus.State.LOCKED, lock_reason = "needs_auth"),
        )

        assertEquals("secret", commandSender.lastPassphrase)
        assertEquals(10, commandSender.lastBoots)
        assertEquals(24, commandSender.lastHours)
    }

    @Test
    fun `LOCKED with getPassphrase throwing falls back to Locked state`() {
        radioService.setDeviceAddress(testDeviceAddress)
        passphraseStore.getThrows = RuntimeException("crypto failure")

        coordinator.handleLockdownStatus(
            LockdownStatus(state = LockdownStatus.State.LOCKED, lock_reason = "needs_auth"),
        )

        assertIs<LockdownState.Locked>(serviceRepo.lockdownState.value)
        assertNull(commandSender.lastPassphrase)
    }

    // endregion

    // region UNLOCKED

    @Test
    fun `UNLOCKED after submitPassphrase saves passphrase and sets authorized`() {
        radioService.setDeviceAddress(testDeviceAddress)
        coordinator.submitPassphrase("mypass", boots = 20, hours = 48)

        coordinator.handleLockdownStatus(
            LockdownStatus(
                state = LockdownStatus.State.UNLOCKED,
                boots_remaining = 19,
                valid_until_epoch = 1_700_000_000,
            ),
        )

        assertTrue(serviceRepo.sessionAuthorized.value)
        assertIs<LockdownState.Unlocked>(serviceRepo.lockdownState.value)
        assertTrue(connectionManager.configOnlyCalled)

        val stored = passphraseStore.saved[testDeviceAddress]
        assertEquals("mypass", stored?.passphrase)
        assertEquals(20, stored?.boots)
        assertEquals(48, stored?.hours)

        val tokenInfo = serviceRepo.lockdownTokenInfo.value
        assertEquals(19, tokenInfo?.bootsRemaining)
        assertEquals(1_700_000_000L, tokenInfo?.expiryEpoch)
    }

    @Test
    fun `UNLOCKED after auto-replay does not overwrite stored passphrase`() {
        radioService.setDeviceAddress(testDeviceAddress)
        passphraseStore.saved[testDeviceAddress] = StoredPassphrase("original", 50, 0)

        // Trigger auto-replay
        coordinator.handleLockdownStatus(
            LockdownStatus(state = LockdownStatus.State.LOCKED, lock_reason = "needs_auth"),
        )
        // Then unlock succeeds
        coordinator.handleLockdownStatus(LockdownStatus(state = LockdownStatus.State.UNLOCKED, boots_remaining = 49))

        // Store should still have original values (pendingPassphrase was null during auto-replay)
        assertEquals("original", passphraseStore.saved[testDeviceAddress]?.passphrase)
        assertTrue(serviceRepo.sessionAuthorized.value)
    }

    @Test
    fun `UNLOCKED with savePassphrase throwing still authorizes session`() {
        radioService.setDeviceAddress(testDeviceAddress)
        passphraseStore.saveThrows = RuntimeException("disk full")
        coordinator.submitPassphrase("mypass", boots = 10, hours = 0)

        coordinator.handleLockdownStatus(LockdownStatus(state = LockdownStatus.State.UNLOCKED))

        // Session should still be authorized even if save fails
        assertTrue(serviceRepo.sessionAuthorized.value)
        assertIs<LockdownState.Unlocked>(serviceRepo.lockdownState.value)
    }

    @Test
    fun `UNLOCKED converts uint32 epoch correctly`() {
        coordinator.submitPassphrase("p", boots = 1, hours = 1)
        // Use a large unsigned value that would be negative as Int: 0xFFFF_FFFF = -1 as Int
        coordinator.handleLockdownStatus(LockdownStatus(state = LockdownStatus.State.UNLOCKED, valid_until_epoch = -1))

        // -1 as Int -> toUInt().toLong() = 4_294_967_295L
        val tokenInfo = serviceRepo.lockdownTokenInfo.value
        assertEquals(4_294_967_295L, tokenInfo?.expiryEpoch)
    }

    // endregion

    // region UNLOCK_FAILED — manual

    @Test
    fun `UNLOCK_FAILED with no backoff sets UnlockFailed state`() {
        coordinator.submitPassphrase("wrong", boots = 10, hours = 0)
        coordinator.handleLockdownStatus(
            LockdownStatus(state = LockdownStatus.State.UNLOCK_FAILED, backoff_seconds = 0),
        )
        assertIs<LockdownState.UnlockFailed>(serviceRepo.lockdownState.value)
    }

    @Test
    fun `UNLOCK_FAILED with backoff sets UnlockBackoff state`() {
        coordinator.submitPassphrase("wrong", boots = 10, hours = 0)
        coordinator.handleLockdownStatus(
            LockdownStatus(state = LockdownStatus.State.UNLOCK_FAILED, backoff_seconds = 30),
        )
        val state = serviceRepo.lockdownState.value
        assertIs<LockdownState.UnlockBackoff>(state)
        assertEquals(30, state.backoffSeconds)
    }

    @Test
    fun `submit after unlock failure saves the replacement passphrase on subsequent success`() {
        radioService.setDeviceAddress(testDeviceAddress)

        coordinator.submitPassphrase("wrong", boots = 10, hours = 0)
        coordinator.handleLockdownStatus(
            LockdownStatus(state = LockdownStatus.State.UNLOCK_FAILED, backoff_seconds = 0),
        )

        coordinator.submitPassphrase("correct", boots = 25, hours = 12)
        coordinator.handleLockdownStatus(
            LockdownStatus(state = LockdownStatus.State.UNLOCKED, boots_remaining = 24, valid_until_epoch = 1234),
        )

        val stored = passphraseStore.saved[testDeviceAddress]
        assertEquals("correct", stored?.passphrase)
        assertEquals(25, stored?.boots)
        assertEquals(12, stored?.hours)
    }

    // endregion

    // region UNLOCK_FAILED — auto-replay

    @Test
    fun `auto-unlock UNLOCK_FAILED with no backoff clears stored passphrase`() {
        radioService.setDeviceAddress(testDeviceAddress)
        passphraseStore.saved[testDeviceAddress] = StoredPassphrase("stale", 5, 0)

        // Trigger auto-replay
        coordinator.handleLockdownStatus(
            LockdownStatus(state = LockdownStatus.State.LOCKED, lock_reason = "needs_auth"),
        )
        // Then failure
        coordinator.handleLockdownStatus(
            LockdownStatus(state = LockdownStatus.State.UNLOCK_FAILED, backoff_seconds = 0),
        )

        assertNull(passphraseStore.saved[testDeviceAddress])
        assertIs<LockdownState.Locked>(serviceRepo.lockdownState.value)
    }

    @Test
    fun `auto-unlock UNLOCK_FAILED with backoff sets UnlockBackoff state`() {
        radioService.setDeviceAddress(testDeviceAddress)
        passphraseStore.saved[testDeviceAddress] = StoredPassphrase("stale", 5, 0)

        coordinator.handleLockdownStatus(LockdownStatus(state = LockdownStatus.State.LOCKED))
        coordinator.handleLockdownStatus(
            LockdownStatus(state = LockdownStatus.State.UNLOCK_FAILED, backoff_seconds = 60),
        )

        val state = serviceRepo.lockdownState.value
        assertIs<LockdownState.UnlockBackoff>(state)
        assertEquals(60, state.backoffSeconds)
    }

    @Test
    fun `auto-unlock UNLOCK_FAILED with clearPassphrase throwing still sets Locked state`() {
        radioService.setDeviceAddress(testDeviceAddress)
        passphraseStore.saved[testDeviceAddress] = StoredPassphrase("stale", 5, 0)
        passphraseStore.clearThrows = RuntimeException("crypto failure")

        coordinator.handleLockdownStatus(LockdownStatus(state = LockdownStatus.State.LOCKED))
        coordinator.handleLockdownStatus(
            LockdownStatus(state = LockdownStatus.State.UNLOCK_FAILED, backoff_seconds = 0),
        )

        assertIs<LockdownState.Locked>(serviceRepo.lockdownState.value)
    }

    // endregion

    // region Lock Now

    @Test
    fun `lockNow followed by LOCKED triggers LockNowAcknowledged`() {
        coordinator.lockNow()
        assertTrue(commandSender.lockNowCalled)

        coordinator.handleLockdownStatus(
            LockdownStatus(state = LockdownStatus.State.LOCKED, lock_reason = "needs_auth"),
        )

        assertIs<LockdownState.LockNowAcknowledged>(serviceRepo.lockdownState.value)
        assertEquals(false, serviceRepo.sessionAuthorized.value)
        assertTrue(connectionManager.clearRadioConfigCalled)
    }

    @Test
    fun `lockNow flag resets after onConnect`() {
        coordinator.lockNow()
        coordinator.onConnect()

        // After reconnect, LOCKED should not trigger LockNowAcknowledged
        radioService.setDeviceAddress(testDeviceAddress)
        coordinator.handleLockdownStatus(
            LockdownStatus(state = LockdownStatus.State.LOCKED, lock_reason = "needs_auth"),
        )

        assertIs<LockdownState.Locked>(serviceRepo.lockdownState.value)
    }

    // endregion

    // region submitPassphrase

    @Test
    fun `submitPassphrase sends command and clears lockNow flag`() {
        coordinator.lockNow()
        coordinator.submitPassphrase("test", boots = 5, hours = 12)

        assertEquals("test", commandSender.lastPassphrase)
        assertEquals(5, commandSender.lastBoots)
        assertEquals(12, commandSender.lastHours)

        // Subsequent LOCKED should not trigger LockNowAcknowledged
        radioService.setDeviceAddress(testDeviceAddress)
        coordinator.handleLockdownStatus(LockdownStatus(state = LockdownStatus.State.LOCKED))
        assertIs<LockdownState.Locked>(serviceRepo.lockdownState.value)
    }

    // endregion
}
