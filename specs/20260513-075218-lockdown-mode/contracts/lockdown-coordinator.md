# Contract: LockdownCoordinator

**Module**: `core/repository` (interface) / `core/data` (implementation)  
**Source set**: `commonMain`

## Interface

```kotlin
package org.meshtastic.core.repository

import org.meshtastic.proto.LockdownStatus

/**
 * Single owner of lockdown lifecycle. Receives firmware LockdownStatus messages,
 * manages state transitions, drives auto-replay of cached passphrases, and updates
 * ServiceRepository state flows for UI consumption.
 *
 * Threading: All public methods are called from the BLE/radio dispatcher
 * (single-threaded). @Volatile fields ensure visibility if a coroutine resumes
 * on a different thread, but compound read-modify sequences assume no concurrent
 * callers.
 */
interface LockdownCoordinator {

    /** Called when a new BLE/radio connection is established. Clears session authorization. */
    fun onConnect()

    /** Called on connection disconnect. Resets all lockdown state for next connection. */
    fun onDisconnect()

    /** Called when config-complete is received. Retained for lifecycle symmetry (currently no-op). */
    fun onConfigComplete()

    /**
     * Called by FromRadioPacketHandler when a LockdownStatus proto arrives.
     * Drives state transitions and may trigger auto-replay.
     */
    fun handleLockdownStatus(status: LockdownStatus)

    /**
     * Submit a passphrase for unlock or provision.
     * Stores pending passphrase for cache-on-success, sends via CommandSender.
     *
     * @param passphrase Passphrase string (1-64 UTF-8 bytes on wire)
     * @param boots Boot-count TTL; default 50
     * @param hours Hours until expiry; 0 = no time limit
     */
    fun submitPassphrase(passphrase: String, boots: Int, hours: Int)

    /** Send lock-now command. Sets wasLockNow flag so next LOCKED routes to LockNowAcknowledged. */
    fun lockNow()
}
```

## Behavioral Contract

1. **Initial state**: `LockdownState.None` — lockdown not active until first `handleLockdownStatus()` call
2. **Lifecycle**: `onConnect()` clears session auth → firmware sends `LockdownStatus` → `onDisconnect()` resets to `None`
3. **State management**: Coordinator updates `ServiceRepository.lockdownState`, `sessionAuthorized`, and `lockdownTokenInfo` flows. UI observes these via ViewModel.
4. **Auto-replay**: When `LOCKED` received and `LockdownPassphraseStore.getPassphrase(deviceAddress)` returns non-null, automatically sends stored passphrase via `CommandSender.sendLockdownPassphrase()`. Sets `wasAutoAttempt=true` to distinguish from manual entry.
5. **Cache management**: On `UNLOCKED` after manual submit (pendingPassphrase != null) → `store.savePassphrase()`. On `UNLOCK_FAILED` after auto-replay with no backoff → `store.clearPassphrase()`.
6. **Lock-now flow**: `lockNow()` → `CommandSender.sendLockNow()` → set `wasLockNow=true` → on next `LOCKED`: route to `handleLockNowAcknowledged()` → clear auth, clear radio config, set `LockdownState.LockNowAcknowledged`
7. **Error resilience**: All `passphraseStore` calls wrapped in try/catch. Store failures don't crash sessions. Save failure during unlock still authorizes session.
8. **Thread safety**: `@Volatile` fields for cross-thread visibility. Single-threaded dispatcher contract documented on impl class.
9. **Logging**: MUST NOT log passphrase content. Logs state transitions and lock reasons.
