# Quickstart: Lockdown Mode

**Feature**: Lockdown Mode  
**Date**: 2026-05-13

## Prerequisites

- JDK 21 installed, `ANDROID_HOME` set
- Proto submodule initialized: `git submodule update --init`
- `local.properties` exists (copy from `secrets.defaults.properties` if missing)
- Proto submodule bumped to revision containing `LockdownAuth` (admin.proto tag 104) and `LockdownStatus` (mesh.proto tag 18). See protobufs#911.

## Quick Verification

```bash
# Full build + test cycle for all touched modules
./gradlew spotlessApply detekt assembleDebug test allTests

# Module-specific checks
./gradlew :core:model:allTests
./gradlew :core:repository:allTests
./gradlew :core:data:allTests
./gradlew :core:datastore:allTests
./gradlew :feature:settings:allTests
```

## Implementation Order

1. **`core/model`** — `LockdownState` sealed class (no dependencies)
2. **`core/repository`** — `LockdownCoordinator` interface + `LockdownPassphraseStore` interface
3. **`core/datastore`** — Platform implementations of `LockdownPassphraseStore` (Android real, JVM/iOS stubs)
4. **`core/data`** — `LockdownCoordinatorImpl` (state machine, auto-replay logic)
5. **`core/data`** — Wire `FromRadioPacketHandlerImpl` to route `lockdown_status` to coordinator
6. **`feature/settings`** — `LockdownDialog` (non-dismissable AlertDialog), `LockdownSessionStatus`, `LockNowButton`
7. **App shell** — Show `LockdownDialog` when lockdown state requires auth
8. **Banner gating** — Add `isAuthorized` checks to action-prompting banners

## Key Files to Modify

| File | Change |
|------|--------|
| `core/data/.../FromRadioPacketHandlerImpl.kt` | Add `lockdown_status` branch in `when` block |
| `core/data/.../CommandSenderImpl.kt` | Add `sendLockdownAuth()` helper (or inline in coordinator) |
| `feature/settings/.../SecurityConfigScreen.kt` | Add `LockdownSessionStatus` + `LockNowButton` |
| App top-level composable | Add lockdown state observation + `LockdownScreen` overlay |

## Key Files to Create

| File | Module | Source Set |
|------|--------|-----------|
| `LockdownState.kt` | `core/model` | commonMain |
| `LockdownCoordinator.kt` | `core/repository` | commonMain |
| `LockdownPassphraseStore.kt` | `core/repository` | commonMain |
| `LockdownCoordinatorImpl.kt` | `core/data` | commonMain |
| `LockdownPassphraseStoreImpl.kt` | `core/datastore` | androidMain |
| `LockdownPassphraseStoreImpl.kt` | `core/datastore` | jvmMain |
| `LockdownPassphraseStoreImpl.kt` | `core/datastore` | iosMain |
| `LockdownScreen.kt` | `feature/settings` | commonMain |
| `LockdownSessionStatus.kt` | `feature/settings` | commonMain |
| `LockNowButton.kt` | `feature/settings` | commonMain |

## Testing Strategy

### Unit Tests (commonMain)

- `LockdownCoordinatorImpl` state machine transitions
- Auto-replay logic (cached passphrase → auto-submit on LOCKED)
- Cache-clear-on-failure logic (UNLOCK_FAILED after auto-replay → clear)
- Lock-now flag tracking (wasLockNow → LockNowAcknowledged on LOCKED)
- Backoff enforcement (timer expires before retry allowed)

### Integration Testing

Requires a device flashed with LOCKDOWN firmware build:
- Provision flow (fresh device → set passphrase → UNLOCKED)
- Unlock flow (locked device → enter passphrase → UNLOCKED)
- Auto-replay (disconnect → reconnect → auto-unlocked without prompt)
- Wrong passphrase (→ UNLOCK_FAILED, retry)
- Backoff (multiple wrong attempts → countdown)
- Lock Now (→ device reboots → next connection requires auth)
- Token expiry (set short TTL → reboot past limit → LOCKED)

## Dependencies

| Dependency | Module | Purpose |
|-----------|--------|---------|
| `androidx.security:security-crypto` | `core/datastore` (androidMain) | EncryptedSharedPreferences |
| Wire-generated protos | `core/proto` | `LockdownAuth`, `LockdownStatus`, `AdminMessage` |

## Common Pitfalls

1. **Proto submodule not bumped**: `LockdownAuth` and `LockdownStatus` don't exist until the proto submodule includes protobufs#911. Build will fail with unresolved references.
2. **`when` exhaustiveness**: New `ModemPreset` enum entries from the proto bump will break exhaustive `when` blocks in `Channel.kt`, `ChannelOption.kt`, `ModelExtensions.kt`. Fix those separately from lockdown changes.
3. **Passphrase encoding**: Proto defines `bytes passphrase = 1`. Use `ByteString` / `ByteArray` directly — do NOT convert to/from UTF-8 String (passphrases may contain arbitrary bytes).
4. **Node ID for local device**: Use `serviceRepository.myNodeNum` (or equivalent) as `destNum` when sending admin messages to the locally-connected node.
5. **Testing without hardware**: The lockdown state machine can be unit-tested by mocking the `LockdownPassphraseStore` and calling `handleStatus()` directly with constructed `LockdownStatus` protos.
