# Data Model: Lockdown Mode

**Feature**: Lockdown Mode  
**Date**: 2026-05-13

## Domain Entities

### LockdownState

The current implementation models lockdown UI state with a sealed class in `core/model`.

| Variant | Fields | Description |
|---------|--------|-------------|
| `None` | — | No active lockdown prompt for the current connection |
| `NeedsProvision` | — | Node requires initial passphrase provisioning |
| `Locked` | `lockReason: String` | Node is locked and awaiting authentication |
| `Unlocked` | — | Current BLE session is authorized |
| `UnlockFailed` | — | Firmware rejected the submitted passphrase and allows immediate retry |
| `UnlockBackoff` | `backoffSeconds: Int` | Firmware rejected the passphrase and rate-limited retries |
| `LockNowAcknowledged` | — | Lock-now was acknowledged; client should disconnect and clear session state |

### LockdownTokenInfo

Session TTL metadata is stored separately from `LockdownState`.

| Field | Type | Description |
|-------|------|-------------|
| `bootsRemaining` | `Int` | Reboots remaining before the token expires |
| `expiryEpoch` | `Long` | Unix epoch seconds when the token expires; `0` means no time limit |

### StoredPassphrase

Encrypted cached passphrase metadata keyed by connected device address.

| Field | Type | Description |
|-------|------|-------------|
| `passphrase` | `String` | Non-empty passphrase string |
| `boots` | `Int` | Provisioning boot TTL cached alongside the passphrase |
| `hours` | `Int` | Provisioning hour TTL cached alongside the passphrase |

**Storage key**: sanitized device address string, not mesh node number.

## Proto Mapping

### FromRadio.lockdown_status -> ServiceRepository state

| Proto `LockdownStatus.State` | Result |
|------------------------------|--------|
| `NEEDS_PROVISION` | `lockdownState = NeedsProvision` |
| `LOCKED` | auto-replay cached passphrase when available; otherwise `lockdownState = Locked(lockReason)` |
| `UNLOCKED` | `lockdownState = Unlocked`, `sessionAuthorized = true`, `lockdownTokenInfo = LockdownTokenInfo(...)` |
| `UNLOCK_FAILED` with `backoff_seconds > 0` | `lockdownState = UnlockBackoff(backoffSeconds)` |
| `UNLOCK_FAILED` with `backoff_seconds == 0` | `lockdownState = UnlockFailed` for manual submits; `Locked()` after failed auto-replay |
| `STATE_UNSPECIFIED` | No state change; warning logged |

### LockdownAuth -> AdminMessage (outgoing)

| Operation | `passphrase` | `boots_remaining` | `valid_until_epoch` | `lock_now` |
|-----------|-------------|-------------------|--------------------|-----------|
| Provision | user-entered UTF-8 string (1-64 bytes) | UI-provided `boots` | UI-provided `hours` mapped by firmware/client contract | `false` |
| Unlock | user-entered UTF-8 string | cached or submitted `boots` | cached or submitted `hours` | `false` |
| Auto-replay | cached `StoredPassphrase.passphrase` | cached `boots` | cached `hours` | `false` |
| Lock Now | empty / ignored | `0` | `0` | `true` |

## Relationships

```text
FromRadioPacketHandlerImpl -> LockdownCoordinator.handleLockdownStatus()
LockdownCoordinatorImpl -> LockdownPassphraseStore
LockdownCoordinatorImpl -> CommandSender
LockdownCoordinatorImpl -> ServiceRepository
LockdownCoordinatorImpl -> Lazy<MeshConnectionManager>  (breaks DI cycle)
UIViewModel / ConnectionsViewModel -> ServiceRepository.lockdownState
RadioConfigViewModel -> ServiceRepository.lockdownTokenInfo / sessionAuthorized
LockdownDialog -> UIViewModel.sendLockdownUnlock() / disconnect callback
SecurityConfigScreen -> RadioConfigViewModel.sendLockNow()
```
