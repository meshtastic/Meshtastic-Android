# Quickstart: Lockdown Mode

**Feature**: Lockdown Mode  
**Date**: 2026-05-13

## Prerequisites

- JDK 21 installed, `ANDROID_HOME` set
- Proto submodule initialized: `git submodule update --init`
- `local.properties` exists (copy from `secrets.defaults.properties` if missing)
- Proto submodule includes `LockdownAuth` and `LockdownStatus`

## Quick Verification

```bash
# Full build + test cycle for all touched modules
./gradlew spotlessApply detekt assembleDebug test allTests

# Module-specific checks
./gradlew :core:model:allTests
./gradlew :core:repository:allTests
./gradlew :core:data:allTests
./gradlew :core:service:jvmTest
./gradlew :feature:settings:allTests
```

## Implementation Order

1. **`core/model`** — `LockdownState` and `LockdownTokenInfo`
2. **`core/repository`** — `LockdownCoordinator` + `LockdownPassphraseStore` interfaces
3. **`core/service`** — Android and JVM `LockdownPassphraseStoreImpl`
4. **`core/data`** — `LockdownCoordinatorImpl` state machine and packet routing
5. **`feature/settings`** — `LockdownDialog` and `LockdownSessionStatus`
6. **App shell / view models** — expose `lockdownState`, unlock action, and lock-now action

## Key Files to Modify

| File | Change |
|------|--------|
| `core/data/.../FromRadioPacketHandlerImpl.kt` | Route `lockdown_status` and `config_complete_id` lifecycle events to the coordinator |
| `core/data/.../CommandSenderImpl.kt` | Add `sendLockdownPassphrase()` and `sendLockNow()` helpers |
| `feature/settings/.../SecurityConfigScreen.kt` | Add `LockdownSessionStatus` and Lock Now action |
| App top-level composable | Observe `lockdownState` and show `LockdownDialog` overlay |

## Key Files Created

| File | Module | Source Set |
|------|--------|-----------|
| `LockdownState.kt` | `core/model` | commonMain |
| `LockdownCoordinator.kt` | `core/repository` | commonMain |
| `LockdownPassphraseStore.kt` | `core/repository` | commonMain |
| `LockdownCoordinatorImpl.kt` | `core/data` | commonMain |
| `LockdownPassphraseStoreImpl.kt` | `core/service` | androidMain |
| `LockdownPassphraseStoreImpl.kt` | `core/service` | jvmMain |
| `LockdownDialog.kt` | `feature/settings` | commonMain |
| `LockdownSessionStatus.kt` | `feature/settings` | commonMain |

## Testing Strategy

### Unit Tests

- `LockdownCoordinatorImpl` state machine transitions
- Auto-replay logic (cached passphrase -> auto-submit on LOCKED)
- Cache-clear-on-failure logic (UNLOCK_FAILED after auto-replay -> clear)
- Lock-now flag tracking (`wasLockNow` -> `LockNowAcknowledged` on LOCKED)
- Backoff state transitions and retry flow
- JVM passphrase store round-trip (`save -> get -> clear`)

### Integration Testing

Requires a device flashed with lockdown-capable firmware:
- Provision flow (fresh device -> set passphrase -> UNLOCKED)
- Unlock flow (locked device -> enter passphrase -> UNLOCKED)
- Auto-replay (disconnect -> reconnect -> auto-unlocked without prompt)
- Wrong passphrase (-> UNLOCK_FAILED, retry)
- Backoff (multiple wrong attempts -> countdown)
- Lock Now (-> device reboots -> next connection requires auth)

## Dependencies

| Dependency | Module | Purpose |
|-----------|--------|---------|
| `androidx.security:security-crypto` | `core/service` (androidMain) | EncryptedSharedPreferences |
| Wire-generated protos | `core/proto` | `LockdownAuth`, `LockdownStatus`, `AdminMessage` |

## Common Pitfalls

1. **Proto submodule not bumped**: `LockdownAuth` and `LockdownStatus` must exist in the current proto revision.
2. **Passphrase validation**: The current UI enforces a maximum of 64 UTF-8 bytes for both passphrase and confirmation fields.
3. **Storage keying**: Cached passphrases are keyed by connected device address, not mesh node number.
4. **Testing without hardware**: The lockdown state machine can be unit-tested by mocking the `LockdownPassphraseStore` and calling `handleLockdownStatus()` directly with constructed `LockdownStatus` protos.
