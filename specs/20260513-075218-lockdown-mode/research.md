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

**Decision**: Add `proto.lockdown_status` as a new branch in the `when` block in `FromRadioPacketHandlerImpl`, routing to `LockdownCoordinator.handleLockdownStatus(status)`. Keep it alongside the existing `configCompleteId` lifecycle callback.

**Alternatives considered**:
- Handling inside `configFlowManager.handleConfigComplete()` — rejected because lockdown_status also arrives asynchronously after admin commands, not just during config flow.
- Using a separate packet filter/interceptor — rejected; overengineered for a single field dispatch.

---

### 2. Admin Message Sending Pattern for LockdownAuth

**Question**: What's the correct pattern for sending `AdminMessage.lockdown_auth`?

**Finding**: `CommandSender.sendAdmin()` takes a `destNum`, optional `requestId`, `wantResponse`, and a lambda `initFn: () -> AdminMessage`. The node number for the locally-connected node comes from `ServiceRepository` (myNodeNum). Example:

**Decision**: Expose `CommandSender.sendLockdownPassphrase(passphrase: String, boots: Int, hours: Int)` and `sendLockNow()` helpers. `LockdownCoordinatorImpl` stays synchronous and delegates to those methods; firmware responses still arrive asynchronously via `FromRadio.lockdown_status`.

**Alternatives considered**:
- `sendAdminAwait()` (suspend + await ACK) — rejected because the "response" is a `FromRadio.lockdown_status`, not a standard admin ACK. The coordinator processes it asynchronously via the `handleLockdownStatus()` callback.

---

### 3. Encrypted Passphrase Storage (Platform Patterns)

**Question**: Best approach for per-node encrypted passphrase caching across platforms?

**Finding**:
- **Android**: `EncryptedSharedPreferences` from AndroidX Security Crypto, keyed by sanitized device address with cached passphrase + TTL metadata.
- **JVM/Desktop**: PKCS12 KeyStore + AES-256-GCM encrypted files under the desktop data directory.
- **iOS**: No implementation in this branch.

**Decision**: Interface `LockdownPassphraseStore` in commonMain with `getPassphrase(deviceAddress)`, `savePassphrase(...)`, and `clearPassphrase(deviceAddress)`. Android uses EncryptedSharedPreferences; JVM/Desktop uses PKCS12 + AES-GCM. There is no iOS implementation in this branch.

**Alternatives considered**:
- DataStore Proto with encryption — rejected; DataStore doesn't natively support encryption and adding custom serialization adds complexity for a simple key-value store.
- Multiplatform Keystore library (e.g., multiplatform-settings) — rejected; adds a dependency for one small use case. The interface is trivial to implement per-platform.

---

### 4. Blocking Dialog (Compose Multiplatform Pattern)

**Question**: How to implement a blocking dialog that prevents all navigation in Compose Multiplatform?

**Finding**: The current navigation uses `MeshtasticNavDisplay`. A non-dismissable dialog can be achieved by:
1. Observing `LockdownCoordinator.state` as a `StateFlow<LockdownState>` in the top-level composable
2. When state is `Locked`, `NeedsProvision`, `UnlockFailed`, or `UnlockBackoff`, render a non-dismissable `AlertDialog` with `onDismissRequest = {}` and an explicit Disconnect action
3. The dialog owns its own state (passphrase text, validation, backoff timer)

**Decision**: Show a non-dismissable `AlertDialog` from the app's main content composition when lockdown is active. `onDismissRequest = {}` prevents dismissal; when not active, normal navigation proceeds.

**Alternatives considered**:
- Full-screen Scaffold overlay — rejected; adds unnecessary complexity when AlertDialog achieves the same blocking behavior with `onDismissRequest = {}`.
- Navigation route that blocks back navigation — rejected; adds complexity to the nav graph and doesn't truly "block" since routes can be deep-linked.

---

### 5. LockdownCoordinator State Machine

**Question**: What states does the coordinator need to manage?

**Finding**: Based on the proto contract and spec requirements:

**Decision**: Use `LockdownState.None`, `NeedsProvision`, `Locked(lockReason: String)`, `Unlocked`, `UnlockFailed`, `UnlockBackoff(backoffSeconds)`, and `LockNowAcknowledged`, plus a separate `LockdownTokenInfo`. The coordinator writes these into `ServiceRepository`; ViewModels expose the flows to UI.

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

**Decision**: Use `ServiceRepository.sessionAuthorized` as the canonical gating flag for actions that should only be available after lockdown authentication.

**Alternatives considered**:
- Per-banner individual gating logic — rejected; centralized flag is simpler and less error-prone.
