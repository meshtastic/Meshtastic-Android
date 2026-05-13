# Data Model: Lockdown Mode

**Feature**: Lockdown Mode  
**Date**: 2026-05-13

## Domain Entities

### LockdownState (sealed class)

The core state machine representing the current lockdown status of the connected node.

| Variant | Fields | Description |
|---------|--------|-------------|
| `NotApplicable` | — | Node doesn't support lockdown (no `LockdownStatus` received) |
| `NeedsProvision` | — | First-time setup; no passphrase ever set on this device |
| `Locked` | `lockReason: LockdownStatus.State` | Storage locked or client not authenticated; uses proto enum directly |
| `Unlocking` | — | Auth sent; awaiting firmware response |
| `Unlocked` | `bootsRemaining: UInt`, `validUntilEpoch: UInt` | Authenticated; session active with TTL info |
| `UnlockFailed` | `backoffSeconds: UInt` | Passphrase rejected; optional rate-limit |
| `LockNowPending` | — | Lock-now command sent; awaiting firmware ACK |
| `LockNowAcknowledged` | — | Firmware confirmed lock; will disconnect |

**State Transitions:**

```
                    ┌─────────────────────┐
                    │    NotApplicable     │ (no LockdownStatus ever received)
                    └─────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│  FromRadio.lockdown_status received                                  │
└─────────────────────────────────────────────────────────────────────┘
         │                    │                        │
         ▼                    ▼                        ▼
  ┌──────────────┐   ┌──────────────┐        ┌──────────────┐
  │NeedsProvision│   │    Locked    │        │   Unlocked   │
  └──────┬───────┘   └──────┬───────┘        └──────┬───────┘
         │                   │                       │
         │ user submits      │ user submits /        │ user presses
         │ passphrase        │ auto-replay           │ "Lock Now"
         ▼                   ▼                       ▼
  ┌──────────────┐   ┌──────────────┐        ┌──────────────┐
  │  Unlocking   │   │  Unlocking   │        │LockNowPending│
  └──────┬───────┘   └──────┬───────┘        └──────┬───────┘
         │                   │                       │
         │ UNLOCKED          │ UNLOCK_FAILED         │ LOCKED (with
         ▼                   ▼                       │ wasLockNow set)
  ┌──────────────┐   ┌──────────────┐               ▼
  │   Unlocked   │   │ UnlockFailed │        ┌────────────────────┐
  └──────────────┘   └──────┬───────┘        │LockNowAcknowledged │
                             │                └────────┬───────────┘
                             │ retry                    │
                             ▼                         │ disconnect
                      ┌──────────────┐                ▼
                      │    Locked    │          (connection closed)
                      └──────────────┘
```

**Validation Rules:**
- `passphrase`: 1-32 bytes (non-empty for provision/unlock, ignored for lock-now)
- `bootsRemaining`: 0 = firmware default; any positive value accepted
- `validUntilEpoch`: 0 = no time limit; positive = absolute Unix seconds
- `backoffSeconds`: 0 = no backoff (immediate retry allowed); >0 = enforced wait

---

### LockdownSession (data class)

Represents the active session info displayed to the user after successful unlock.

| Field | Type | Description |
|-------|------|-------------|
| `bootsRemaining` | `UInt` | Reboots before token expires (decrements per boot) |
| `validUntilEpoch` | `UInt` | Unix epoch seconds when token expires; 0 = no time limit |

**Derived properties:**
- `hasTimeLimit: Boolean` = `validUntilEpoch > 0u`
- `isBootLimited: Boolean` = `bootsRemaining > 0u`

---

### CachedPassphrase (per-node storage)

| Field | Type | Description |
|-------|------|-------------|
| `nodeId` | `Int` | Node number (mesh address) used as storage key |
| `passphrase` | `ByteArray` | Raw passphrase bytes (1-32), encrypted at rest |

**Storage key format:** `"lockdown_${nodeId.toUInt().toString(16)}"` (hex node ID)

**Lifecycle:**
- Created/updated on successful unlock (UNLOCKED received after user-entered passphrase)
- Read on reconnection (LOCKED received → auto-replay attempt)
- Deleted when auto-replay fails (UNLOCK_FAILED after cached passphrase sent)
- Never logged or exposed in debug output

---

## Proto Mapping

### FromRadio.lockdown_status → LockdownState

| Proto `LockdownStatus.State` | Maps to `LockdownState` |
|------------------------------|-------------------------|
| `NEEDS_PROVISION` | `NeedsProvision` |
| `LOCKED` | `Locked(reason = status.lock_reason)` |
| `UNLOCKED` | `Unlocked(bootsRemaining = status.boots_remaining, validUntilEpoch = status.valid_until_epoch)` |
| `UNLOCK_FAILED` | `UnlockFailed(backoffSeconds = status.backoff_seconds)` |
| `STATE_UNSPECIFIED` | Treated as `Locked(reason = "unknown")` |

### LockdownAuth → AdminMessage (outgoing)

| Operation | `passphrase` | `boots_remaining` | `valid_until_epoch` | `lock_now` |
|-----------|-------------|-------------------|--------------------|-----------| 
| Provision | user-entered (1-32 bytes) | user-entered or 0 | user-entered or 0 | `false` |
| Unlock | user-entered (1-32 bytes) | 0 (firmware default) | 0 (no limit) | `false` |
| Auto-replay | cached bytes | 0 | 0 | `false` |
| Lock Now | empty/ignored | 0 | 0 | `true` |

---

## Relationships

```
LockdownCoordinator (1) ──owns──▶ LockdownState (1, current)
LockdownCoordinator (1) ──uses──▶ LockdownPassphraseStore (1)
LockdownCoordinator (1) ──uses──▶ CommandSender (1, for sending AdminMessage)
LockdownCoordinator (1) ──uses──▶ ConnectionManager (1, for disconnect on lock-now)
FromRadioPacketHandler (1) ──calls──▶ LockdownCoordinator.handleStatus()
UI (LockdownDialog) ──observes──▶ LockdownCoordinator.state (StateFlow)
UI (LockdownDialog) ──calls──▶ LockdownCoordinator.submitPassphrase()
UI (LockNowButton) ──calls──▶ LockdownCoordinator.lockNow()
SecurityConfigScreen ──observes──▶ LockdownCoordinator.state (for session info)
```
