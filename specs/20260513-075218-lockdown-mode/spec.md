# Feature Specification: Lockdown Mode

**Feature Branch**: `features/lockdown-v2`  
**Created**: 2026-05-13  
**Status**: Draft  
**Input**: User description: "Implement lockdown mode using new lockdown protobufs and Nick's draft PR (#5439) as the baseline"  
**Cross-Platform Spec**: N/A — platform-specific client implementation of firmware-driven lockdown protocol

## Summary

Lockdown mode protects unattended Meshtastic nodes from unauthorized physical access. When enabled on firmware, a connecting client must provide a passphrase before it can view or modify the node's actual configuration. The Android app needs to detect locked nodes, prompt for authentication, cache credentials securely, display session status, and provide a "Lock Now" action to immediately re-lock the device.

## Clarifications

### Session 2026-05-13

- Q: Should lockdown block all navigation or only gate config screens? → A: Non-dismissable blocking dialog; user must unlock/provision before accessing any app functionality
- Q: Should the app expose TTL fields (boots_remaining, valid_until_epoch) to the user or always use firmware defaults? → A: Optional fields — show "boots remaining" and "hours until expiry" as optional inputs, default to firmware values when left empty
- Q: Should coordinator and passphrase store be full KMP (commonMain interface + expect/actual) or Android-only initially? → A: Full KMP via commonMain interfaces plus platform-specific DI implementations in `androidMain` and `jvmMain`
- Q: Should "Lock Now" use a client-side flag to await firmware ACK, or fire-and-disconnect immediately? → A: Client-side flag — track wasLockNow, route next LOCKED status to "Lock confirmed" state, then disconnect gracefully
- Q: Should all action-prompting banners be gated on lockdown auth, or only the region-unset banner? → A: All action-prompting banners — suppress any banner that asks users to change config they cannot access while locked

### Implementation Sync (2026-05-13)

This spec is aligned to the implementation on `features/lockdown-v2`:

1. `LockdownState` uses `None`, `NeedsProvision`, `Locked(lockReason: String)`, `Unlocked`, `UnlockFailed`, `UnlockBackoff`, and `LockNowAcknowledged`
2. Session TTL metadata is exposed separately as `LockdownTokenInfo(bootsRemaining: Int, expiryEpoch: Long)`
3. `LockdownCoordinator` is a synchronous commonMain interface; reactive state is exposed via `ServiceRepository`
4. `LockdownPassphraseStore` is keyed by device address and stores `String` passphrases plus `boots` / `hours`
5. Platform implementations currently exist for Android and JVM/Desktop in `core/service`; there is no iOS implementation in this branch
6. The blocking UI is a non-dismissable `AlertDialog` using `onDismissRequest = {}` with an explicit Disconnect action

## Goals

1. Enable users to authenticate against locked-down nodes so they can access real device configuration over BLE/USB
2. Allow first-time passphrase provisioning on unprovisioned hardened nodes
3. Provide clear visibility into the current lockdown state (locked, unlocked, session TTL)
4. Allow users to immediately re-lock a device with a single action
5. Securely cache passphrases locally so reconnections don't require re-entry every time

## Non-Goals

- Implementing lockdown logic in firmware (firmware handles encryption, token management, DEK generation)
- Modifying the protobuf definitions (these are read-only upstream in `core/proto`)
- Providing remote lock/unlock over the mesh network (lockdown is local connection only)
- Managing lockdown across multiple nodes simultaneously in a single flow
- Implementing a passphrase strength meter or password policy enforcement

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Unlock a Locked Node (Priority: P1)

A user connects to a node that has lockdown mode enabled and is currently locked. The app detects the `LockdownStatus.LOCKED` state from the firmware and prompts the user to enter the passphrase. Upon successful entry, the node unlocks and the user can view/edit configurations normally.

**Why this priority**: This is the core interaction — without unlock capability, lockdown-enabled nodes are inaccessible from the app.

**Independent Test**: Connect to a locked node via BLE, enter the correct passphrase, and verify that full configuration becomes accessible.

**Acceptance Scenarios**:

1. **Given** the app connects to a node reporting `LockdownStatus.State.LOCKED`, **When** the connection completes and config is received, **Then** the app displays a passphrase entry dialog before allowing access to settings
2. **Given** the user enters the correct passphrase, **When** the `LockdownAuth` admin message is sent, **Then** the firmware responds with `LockdownStatus.State.UNLOCKED` and the app displays the real device configuration
3. **Given** the user enters an incorrect passphrase, **When** the firmware responds with `LockdownStatus.State.UNLOCK_FAILED`, **Then** the app displays an error message and allows retry
4. **Given** the firmware responds with `UNLOCK_FAILED` and a non-zero `backoff_seconds`, **When** the user sees the error, **Then** the app enforces the backoff period before allowing another attempt

---

### User Story 2 - Provision a New Lockdown Passphrase (Priority: P1)

A user connects to a hardened firmware node that has never been provisioned (no passphrase set). The app detects `LockdownStatus.State.NEEDS_PROVISION` and prompts the user to create a passphrase. Upon successful provisioning, the firmware generates a DEK and the node is unlocked for the current session.

**Why this priority**: Without provisioning, a hardened node cannot be secured — this is the setup path.

**Independent Test**: Connect to an unprovisioned node, set a passphrase, and verify the node transitions to UNLOCKED state.

**Acceptance Scenarios**:

1. **Given** the app connects to a node reporting `LockdownStatus.State.NEEDS_PROVISION`, **When** the config complete is received, **Then** the app prompts the user to create a new passphrase
2. **Given** the user enters and confirms a passphrase (1-64 UTF-8 bytes), **When** the `LockdownAuth` message is sent with `lock_now=false`, **Then** the firmware provisions the DEK and responds with `UNLOCKED`
3. **Given** the user is in the provisioning flow, **When** they attempt to set an empty passphrase, **Then** the app prevents submission and shows a validation message

---

### User Story 3 - Lock Now (Priority: P2)

A user who has an unlocked session wants to immediately re-lock the device (e.g., before leaving it unattended). They press a "Lock Now" button in the Security settings. The device revokes all authorization, wipes RAM, and reboots into the locked state.

**Why this priority**: Provides active security control but the device will also lock on its own when the token expires.

**Independent Test**: With an unlocked node, press "Lock Now" and verify the node reboots and subsequent connection requires passphrase.

**Acceptance Scenarios**:

1. **Given** the node is in `UNLOCKED` state, **When** the user presses "Lock Now" in Settings → Security, **Then** the app sends `LockdownAuth(lock_now=true)` and sets a client-side `wasLockNow` flag
2. **Given** the app has sent lock-now and set `wasLockNow`, **When** firmware responds with `LOCKED` status, **Then** the app routes to a "Lock confirmed" state (no passphrase dialog flash) and disconnects gracefully
3. **Given** the user presses "Lock Now", **When** the device reboots, **Then** the next connection attempt shows the node as `LOCKED` requiring re-authentication
4. **Given** the user has not yet unlocked the node, **When** they view Security settings, **Then** the "Lock Now" button is not available (or clearly indicates the device is already locked)

---

### User Story 4 - Cached Passphrase Auto-Reconnect (Priority: P2)

A user who has previously authenticated to a node reconnects (e.g., after a brief disconnection or app restart). The app retrieves the cached passphrase and automatically sends the unlock without prompting the user again.

**Why this priority**: Improves UX for frequent reconnections but is not required for basic functionality.

**Independent Test**: Authenticate to a node, disconnect, reconnect, and verify no passphrase prompt appears.

**Acceptance Scenarios**:

1. **Given** the user previously authenticated with a correct passphrase, **When** the app reconnects and receives `LOCKED` status, **Then** the app automatically replays the cached passphrase
2. **Given** the cached passphrase is no longer valid (firmware reports `UNLOCK_FAILED`), **When** auto-replay fails, **Then** the app clears the cache and prompts the user to enter the passphrase manually
3. **Given** the user has never authenticated to a particular node, **When** connecting for the first time, **Then** no auto-replay occurs and the standard prompt is shown

---

### User Story 5 - View Session Token Status (Priority: P3)

A user with an unlocked session can view the remaining session lifetime (boots remaining, expiry time) in the Security settings area, so they know when re-authentication will be required.

**Why this priority**: Informational — improves awareness but doesn't affect core functionality.

**Independent Test**: Unlock a node and verify the session info (boots remaining, time until expiry) is displayed.

**Acceptance Scenarios**:

1. **Given** the node is `UNLOCKED` with `boots_remaining=5` and `valid_until_epoch` set, **When** the user views Security settings, **Then** the remaining boots and expiry time are displayed in a human-readable format
2. **Given** the node is `UNLOCKED` with `valid_until_epoch=0`, **When** the user views session info, **Then** the app shows "No time limit" for the expiry field

---

### Edge Cases

- What happens when the BLE connection drops mid-authentication? The app should treat the auth as incomplete and re-prompt on reconnect.
- How does the app handle a node that transitions from locked to unlocked by another client? The firmware sends a new `LockdownStatus` which the app processes and updates UI state.
- What if the user's cached passphrase is for a node that has been re-provisioned? Auto-replay fails, cache is cleared, user is prompted.
- What happens if the device clock is wrong and `valid_until_epoch` appears expired? The client displays the firmware-reported state as-is (lockdown decisions are firmware-side).

## Architecture

### Key Components

| Component | Module / File | Purpose |
|-----------|---------------|---------|
| LockdownStatus handler | `core/data/` | Processes `FromRadio.lockdown_status` packets via `FromRadioPacketHandlerImpl` |
| LockdownAuth sender | `core/data/` | Sends `AdminMessage.lockdown_auth` via `CommandSenderImpl` |
| Lockdown UI (dialog) | `feature/settings/` | Passphrase entry/provisioning dialog and session status display |
| Lock Now action | `feature/settings/` | Button in Security settings to trigger immediate re-lock |
| Passphrase cache | `core/service/` | Encrypted local storage of per-device cached passphrases |
| Lockdown state model | `core/model/` | Domain model representing lockdown state for UI consumption |

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: App MUST detect and handle `LockdownStatus` in the `FromRadio` packet stream after config complete
- **FR-002**: App MUST display a passphrase entry dialog when the connected node reports `LOCKED` state
- **FR-003**: App MUST display a passphrase creation dialog when the connected node reports `NEEDS_PROVISION` state
- **FR-004**: App MUST send `LockdownAuth` admin messages with the user-supplied passphrase to unlock/provision
- **FR-005**: App MUST allow configuring `boots` and `hours` when provisioning a passphrase; current UI defaults to `boots = 50` and `hours = 0`
- **FR-006**: App MUST display error feedback when firmware reports `UNLOCK_FAILED`, including backoff countdown when `backoff_seconds > 0`
- **FR-007**: App MUST provide a "Lock Now" action that sends `LockdownAuth(lock_now=true)` to the node
- **FR-008**: App MUST cache passphrases in encrypted local storage, keyed per node
- **FR-009**: App MUST auto-replay cached passphrase on reconnection to a previously-authenticated locked node
- **FR-010**: App MUST clear cached passphrase when auto-replay results in `UNLOCK_FAILED`
- **FR-011**: App MUST display session token TTL info (boots remaining, expiry) when the node is unlocked
- **FR-012**: App MUST present a non-dismissable blocking dialog when in `LOCKED`, `NEEDS_PROVISION`, `UNLOCK_FAILED`, or `UNLOCK_BACKOFF` states, preventing navigation until the user unlocks or disconnects
- **FR-013**: App MUST suppress all action-prompting banners (e.g., "Region Unset", configuration warnings) when the connected node is lockdown-enabled but not yet authorized, since the user cannot act on them

### Non-Functional Requirements

- **NFR-001**: Cached passphrases MUST be stored using platform-appropriate encrypted storage (EncryptedSharedPreferences on Android, encrypted file + PKCS12/AES-GCM on Desktop)
- **NFR-002**: Passphrase entry dialog MUST NOT log or expose passphrase bytes in debug output
- **NFR-003**: Unlock flow MUST complete within 5 seconds on a standard BLE connection (user-perceived latency from submit to unlocked state)

## Source-Set Impact

| Source Set | Impact | Justification |
|-----------|--------|---------------|
| `commonMain` | LockdownCoordinator interface, LockdownState model, passphrase store interface, UI composables (unlock dialog, lock-now button, session status) | All business logic and UI per Constitution §I |
| `androidMain` | `LockdownPassphraseStore` impl (EncryptedSharedPreferences), AIDL plumbing for sendLockdownUnlock/sendLockNow | Platform-specific secure storage + IPC |
| `jvmMain` | `LockdownPassphraseStore` impl (encrypted file or Java KeyStore) | Platform-specific secure storage |

## Design Standards Compliance

- [ ] New screens reviewed against [design standards](https://raw.githubusercontent.com/meshtastic/design/refs/heads/master/standards/meshtastic_design_standards_latest.md)
- [ ] M3 component selection verified (e.g., `OutlinedTextField` for passphrase, `FilledTonalButton` for Lock Now)
- [ ] Accessibility: TalkBack semantics, touch targets, color-independent info
- [ ] Typography: `titleMediumEmphasized` for emphasis, M3 scale for hierarchy

## Privacy Assessment

- [ ] No PII, location data, or cryptographic keys logged or exposed
- [ ] Passphrases stored only in encrypted platform storage, never in plaintext
- [ ] No new network calls that transmit user data (lockdown is local connection only)
- [ ] Proto submodule (`core/proto`) not modified (read-only upstream)

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can unlock a locked node and access full configuration within 10 seconds of entering the correct passphrase
- **SC-002**: Users connecting to an unprovisioned node can set a passphrase and reach unlocked state in a single flow without confusion
- **SC-003**: "Lock Now" action results in the device rebooting to locked state within 5 seconds of user action
- **SC-004**: Returning users with cached passphrase reconnect without manual re-entry in 95% of cases (cache hit)
- **SC-005**: Zero passphrase bytes appear in any application log output at any log level

## Assumptions

- All business logic and UI composables reside in `commonMain` source set
- String resources added to `core/resources/src/commonMain/composeResources/values/strings.xml`
- Icons use `MeshtasticIcons` (from `core/ui/icon/`)
- The firmware correctly implements the `LockdownAuth` / `LockdownStatus` protobuf contract as defined in `admin.proto` and `mesh.proto`
- The existing `FromRadio` packet handling infrastructure can be extended to process the new `lockdown_status` field (field 18)
- Passphrase is limited to 1-64 UTF-8 bytes as enforced by the current UI and firmware contract
- The app does not need to determine whether a node is "hardened" — it simply reacts to `LockdownStatus` presence
- The current provisioning UI defaults TTL parameters to `boots = 50` and `hours = 0`
