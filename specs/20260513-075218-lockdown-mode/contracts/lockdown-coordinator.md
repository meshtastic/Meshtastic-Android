# Contract: LockdownCoordinator

**Module**: `core/repository` (interface) / `core/data` (implementation)  
**Source set**: `commonMain`

## Interface

```kotlin
package org.meshtastic.core.repository

import kotlinx.coroutines.flow.StateFlow
import org.meshtastic.core.model.lockdown.LockdownState

/**
 * Single owner of lockdown lifecycle. Receives firmware status reports,
 * manages state transitions, drives auto-replay, and exposes observable
 * state for UI consumption.
 */
interface LockdownCoordinator {

    /** Current lockdown state. Observed by UI to render blocking modal or session info. */
    val state: StateFlow<LockdownState>

    /**
     * Whether the current connection is authorized (unlocked or lockdown not applicable).
     * Convenience derived from [state] for banner/UI gating.
     */
    val isAuthorized: StateFlow<Boolean>

    /**
     * Called by [FromRadioPacketHandler] when a LockdownStatus proto arrives.
     * Drives state transitions and may trigger auto-replay.
     */
    fun handleStatus(status: org.meshtastic.proto.LockdownStatus)

    /**
     * Called when a new connection is established. Stores nodeId for
     * passphrase cache lookups during auto-replay.
     *
     * @param nodeId The connected node's mesh number
     */
    fun onConnect(nodeId: Int)

    /**
     * Called when config-complete is received from the device.
     * Triggers initial lockdown state evaluation (auto-replay if cached passphrase exists).
     */
    fun onConfigComplete()

    /**
     * Called on connection disconnect. Resets state to [LockdownState.NotApplicable]
     * so next connection starts fresh. Replaces the standalone `reset()` method.
     */
    fun onDisconnect()

    /**
     * Submit a passphrase for unlock or provision.
     * Transitions state to [LockdownState.Unlocking] and sends AdminMessage.
     *
     * @param passphrase Raw passphrase bytes (1-32)
     * @param bootsRemaining Optional boot-count TTL; 0 = firmware default
     * @param validUntilEpoch Optional wall-clock expiry; 0 = no time limit
     */
    suspend fun submitPassphrase(
        passphrase: ByteArray,
        bootsRemaining: UInt = 0u,
        validUntilEpoch: UInt = 0u,
    )

    /**
     * Send lock-now command. Transitions to [LockdownState.LockNowPending],
     * then disconnects after firmware ACK.
     */
    suspend fun lockNow()
}
```

## Behavioral Contract

1. **Initial state**: `LockdownState.NotApplicable` until first `handleStatus()` call
2. **Lifecycle**: `onConnect(nodeId)` stores the node ID → `onConfigComplete()` evaluates initial state → `onDisconnect()` resets to `NotApplicable`
3. **Auto-replay**: When transitioning to `Locked` and `LockdownPassphraseStore.get(nodeId)` returns non-null, automatically call `submitPassphrase()` with cached bytes (boots=0, epoch=0)
4. **Cache management**: On `Unlocked` after user-entered passphrase → `store.put(nodeId, passphrase)`. On `UnlockFailed` after auto-replay → `store.clear(nodeId)`
5. **Lock-now flow**: `lockNow()` → send `LockdownAuth(lock_now=true)` → set `wasLockNow=true` → on next `LOCKED` status: transition to `LockNowAcknowledged` → delay 500ms → disconnect
6. **Thread safety**: All state mutations on a single coroutine dispatcher (no race between handleStatus and user actions)
6. **Logging**: MUST NOT log passphrase bytes. May log state transitions and node IDs (redacted to last 4 hex chars for device addresses).
