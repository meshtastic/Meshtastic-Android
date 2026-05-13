# Research: Lockdown Mode

**Feature**: Lockdown Mode  
**Date**: 2026-05-13  
**Status**: Complete

## Research Tasks

### 1. FromRadio lockdown_status Integration Point

**Question**: Where and how to wire `FromRadio.lockdown_status` (field 18) into the existing packet handling pipeline?

**Finding**: `FromRadioPacketHandlerImpl.handleFromRadio()` uses a `when` block dispatching on non-null proto fields. The `lockdown_status` field arrives as a `LockdownStatus?` from the generated Wire class. It can arrive:
- Immediately after `config_complete_id` (initial connection state report)
- In response to any `AdminMessage.lockdown_auth` sent by the client

**Decision**: Add `proto.lockdown_status` as a new branch in the `when` block in `FromRadioPacketHandlerImpl`, routing to `LockdownCoordinator.handleStatus(status)`. Place after `configCompleteId` handling since that's the natural ordering.

**Alternatives considered**:
- Handling inside `configFlowManager.handleConfigComplete()` — rejected because lockdown_status also arrives asynchronously after admin commands, not just during config flow.
- Using a separate packet filter/interceptor — rejected; overengineered for a single field dispatch.

---

### 2. Admin Message Sending Pattern for LockdownAuth

**Question**: What's the correct pattern for sending `AdminMessage.lockdown_auth`?

**Finding**: `CommandSender.sendAdmin()` takes a `destNum`, optional `requestId`, `wantResponse`, and a lambda `initFn: () -> AdminMessage`. The node number for the locally-connected node comes from `ServiceRepository` (myNodeNum). Example:

```kotlin
commandSender.sendAdmin(myNodeNum, wantResponse = true) {
    AdminMessage(lockdown_auth = LockdownAuth(
        passphrase = passphraseBytes,
        boots_remaining = bootsRemaining,  // 0 = firmware default
        valid_until_epoch = validUntilEpoch,  // 0 = no time limit
        lock_now = false,
    ))
}
```

**Decision**: Add `sendLockdownAuth(passphrase: ByteArray, bootsRemaining: UInt, validUntilEpoch: UInt, lockNow: Boolean)` method to `LockdownCoordinator` which delegates to `commandSender.sendAdmin()`. Use `wantResponse = true` since firmware always replies with `LockdownStatus`.

**Alternatives considered**:
- `sendAdminAwait()` (suspend + await ACK) — rejected because the "response" is a `FromRadio.lockdown_status`, not a standard admin ACK. The coordinator processes it asynchronously via the `handleStatus()` callback.

---

### 3. Encrypted Passphrase Storage (Platform Patterns)

**Question**: Best approach for per-node encrypted passphrase caching across platforms?

**Finding**:
- **Android**: `EncryptedSharedPreferences` from AndroidX Security Crypto. Key = node ID (hex string), value = Base64-encoded passphrase bytes. Already a dependency in the project.
- **JVM/Desktop**: `java.security.KeyStore` with JCEKS type, or simpler: AES-encrypt a JSON file using a key derived from a fixed seed in the app's data directory. For stubs, a no-op (passphrase never cached) is acceptable.
- **iOS**: Keychain Services via `Security` framework. For stubs, no-op is acceptable.

**Decision**: Interface `LockdownPassphraseStore` in commonMain:
```kotlin
interface LockdownPassphraseStore {
    suspend fun get(nodeId: Int): ByteArray?
    suspend fun put(nodeId: Int, passphrase: ByteArray)
    suspend fun clear(nodeId: Int)
}
```
Android: real implementation with EncryptedSharedPreferences.  
JVM/iOS: no-op stubs returning null/doing nothing (passphrase never cached, user always prompted).

**Alternatives considered**:
- DataStore Proto with encryption — rejected; DataStore doesn't natively support encryption and adding custom serialization adds complexity for a simple key-value store.
- Multiplatform Keystore library (e.g., multiplatform-settings) — rejected; adds a dependency for one small use case. The interface is trivial to implement per-platform.

---

### 4. Blocking Dialog (Compose Multiplatform Pattern)

**Question**: How to implement a blocking dialog that prevents all navigation in Compose Multiplatform?

**Finding**: The current navigation uses `MeshtasticNavDisplay`. A non-dismissable dialog can be achieved by:
1. Observing `LockdownCoordinator.state` as a `StateFlow<LockdownState>` in the top-level composable
2. When state is `Locked` or `NeedsProvision`, rendering a non-dismissable `AlertDialog` with `onDismissRequest = {}` and `BackHandler {}` to intercept back presses
3. The dialog owns its own state (passphrase text, validation, backoff timer)

**Decision**: Show a non-dismissable `AlertDialog` from the app's main content composition when lockdown is active. The `onDismissRequest = {}` prevents touch-outside dismiss, and `BackHandler {}` blocks back navigation. When not active (unlocked or no lockdown on this node), normal navigation proceeds.

**Alternatives considered**:
- Full-screen Scaffold overlay — rejected; adds unnecessary complexity when AlertDialog achieves the same blocking behavior with `onDismissRequest = {}`.
- Navigation route that blocks back navigation — rejected; adds complexity to the nav graph and doesn't truly "block" since routes can be deep-linked.

---

### 5. LockdownCoordinator State Machine

**Question**: What states does the coordinator need to manage?

**Finding**: Based on the proto contract and spec requirements:

```
States:
  NotApplicable       — Connected node doesn't use lockdown (no LockdownStatus received)
  NeedsProvision      — NEEDS_PROVISION received; awaiting user passphrase creation
  Locked              — LOCKED received; awaiting user passphrase entry or auto-replay
  Unlocking           — Auth sent; waiting for firmware response
  Unlocked(session)   — UNLOCKED received with boots_remaining + valid_until_epoch
  UnlockFailed(info)  — UNLOCK_FAILED received with optional backoff
  LockNowPending      — Lock-now sent; awaiting LOCKED ACK
  LockNowAcknowledged — ACK received; will disconnect
```

**Decision**: Sealed class `LockdownState` with these variants. The coordinator manages transitions and exposes state as `StateFlow<LockdownState>`. Auto-replay triggers automatically when entering `Locked` state if a cached passphrase exists for the node.

**Alternatives considered**:
- Simpler 3-state model (Locked/Unlocked/None) — rejected; insufficient for backoff enforcement, lock-now ACK tracking, and pending states.

---

### 6. Lock Now Explicit Disconnect

**Question**: How to explicitly disconnect after LockNowAcknowledged?

**Finding**: The existing `MeshConnectionManager` has a `disconnect()` method (or equivalent) that tears down the BLE/Serial/TCP connection. Nick's PR already has the `wasLockNow` flag — just needs one line to call disconnect after transitioning to `LockNowAcknowledged`.

**Decision**: In `LockdownCoordinatorImpl`, when transitioning to `LockNowAcknowledged`: post a short delay (500ms for UX feedback), then call the connection manager's disconnect. This gives the UI a moment to show "Lock confirmed" before the connection drops.

**Alternatives considered**:
- Immediate disconnect (no delay) — acceptable but feels abrupt; user gets no visual confirmation.
- Rely on firmware reboot — rejected per spec; non-deterministic timing.

---

### 7. Banner Gating Architecture

**Question**: How to suppress action-prompting banners when locked?

**Finding**: Banners in the app are typically rendered conditionally in composables. The "Region Unset" banner is in the connections screen. Other potential banners: firmware update prompts, channel configuration warnings.

**Decision**: Expose `isLockdownAuthorized: StateFlow<Boolean>` from `LockdownCoordinator`. This is `true` when state is `Unlocked` or `NotApplicable`, `false` otherwise. Banner composables that prompt user action gate their visibility on this flag. Since the full-screen modal blocks navigation anyway (FR-012), this is a defense-in-depth measure for any briefly-visible content during state transitions.

**Alternatives considered**:
- Per-banner individual gating logic — rejected; centralized flag is simpler and less error-prone.
