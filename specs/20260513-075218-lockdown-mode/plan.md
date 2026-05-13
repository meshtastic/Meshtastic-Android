# Implementation Plan: Lockdown Mode

**Branch**: `feat/lockdown-mode` | **Date**: 2026-05-13 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/20260513-075218-lockdown-mode/spec.md`

## Summary

Implement client-side support for firmware lockdown mode using the typed `LockdownAuth` / `LockdownStatus` protobuf contract. The app detects locked nodes via `FromRadio.lockdown_status`, presents a non-dismissable blocking passphrase dialog, sends `AdminMessage.lockdown_auth` for provision/unlock/lock-now operations, caches passphrases in platform-encrypted storage, and auto-replays on reconnect. Architecture uses a `LockdownCoordinator` interface in `commonMain` with platform-specific passphrase store implementations via expect/actual.

## Technical Context

**Language/Version**: Kotlin 2.3+ (JDK 21)  
**Primary Dependencies**: Compose Multiplatform, Koin 4.2+, Wire (protobuf), Kable (BLE), Okio  
**Storage**: EncryptedSharedPreferences (Android), Keychain (iOS), Java KeyStore (Desktop)  
**Testing**: `./gradlew test allTests` (KMP modules use `:allTests`, Android-only use `:testFdroidDebugUnitTest`)  
**Target Platform**: Android (primary), Desktop (JVM), iOS (future)  
**Project Type**: Mobile/Desktop KMP app  
**Performance Goals**: Unlock flow < 5s user-perceived latency on BLE  
**Constraints**: Passphrase 1-32 bytes, no logging of sensitive data, offline-capable  
**Scale/Scope**: 3 new files in commonMain, 1 expect/actual per platform, UI additions to `feature/settings`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **I. Kotlin Multiplatform Core**: ✅ PASS
  - `commonMain`: `LockdownCoordinator` interface, `LockdownState` sealed class, `LockdownPassphraseStore` interface, UI composables (dialog, lock-now button, session status)
  - `androidMain`: `LockdownPassphraseStoreImpl` (EncryptedSharedPreferences)
  - `jvmMain`: `LockdownPassphraseStoreImpl` (Java KeyStore file-backed)
  - `iosMain`: `LockdownPassphraseStoreImpl` (Keychain) — stub for now
  - No `java.*` or `android.*` imports in commonMain. All business logic in commonMain.

- **II. Zero Lint Tolerance**: ✅ PASS
  - Commands: `./gradlew spotlessApply spotlessCheck detekt`
  - Modules touched: `:core:model`, `:core:repository`, `:core:data`, `:core:datastore`, `:feature:settings`

- **III. Compose Multiplatform UI**: ✅ PASS
  - Lockdown dialog is a non-dismissable `AlertDialog` composable in commonMain (`onDismissRequest = {}` + `BackHandler`)
  - No `NavigationBackHandler` needed (dialog blocks all interaction; dismiss = disconnect)
  - No float formatting needed (TTL displayed as integer boot count / formatted date string)

- **IV. Privacy First**: ✅ PASS
  - Passphrases stored only in encrypted platform storage, never logged
  - No modification to `core/proto` (read-only submodule)
  - No PII exposure — node IDs used as cache keys (already public on mesh)

- **V. Design Standards Compliance**: ✅ PASS
  - Cross-Platform Spec: N/A — platform-specific client UI for firmware protocol (lockdown is transport-layer, not a mesh behavior)
  - UI uses M3 components: `OutlinedTextField` (passphrase), `FilledTonalButton` (Lock Now), `AlertDialog` (errors)
  - Accessibility: password field with content description, touch targets met

- **VI. Verify Before Push**: ✅ PASS
  - Local: `./gradlew spotlessApply detekt assembleDebug test allTests`
  - Post-push: `gh pr checks <PR>` or `gh run list --branch feat/lockdown-mode --limit 5`

## Project Structure

### Documentation (this feature)

```text
specs/20260513-075218-lockdown-mode/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output (internal interfaces)
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Source Code (repository root)

```text
core/model/src/commonMain/kotlin/org/meshtastic/core/model/
└── lockdown/
    └── LockdownState.kt                    # Sealed class for lockdown states

core/repository/src/commonMain/kotlin/org/meshtastic/core/repository/
├── LockdownCoordinator.kt                  # Interface: lockdown lifecycle owner
└── LockdownPassphraseStore.kt              # Interface: encrypted per-node cache

core/data/src/commonMain/kotlin/org/meshtastic/core/data/manager/
└── LockdownCoordinatorImpl.kt              # Implementation: state machine, auto-replay

core/datastore/src/androidMain/kotlin/org/meshtastic/core/datastore/
└── LockdownPassphraseStoreImpl.kt          # EncryptedSharedPreferences impl

core/datastore/src/jvmMain/kotlin/org/meshtastic/core/datastore/
└── LockdownPassphraseStoreImpl.kt          # Java KeyStore impl

core/datastore/src/iosMain/kotlin/org/meshtastic/core/datastore/
└── LockdownPassphraseStoreImpl.kt          # Keychain impl (stub)

feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/
└── lockdown/
    ├── LockdownDialog.kt                   # Non-dismissable AlertDialog passphrase entry/provision modal
    ├── LockdownSessionStatus.kt            # Session TTL display composable
    └── LockNowButton.kt                    # Lock Now action in Security settings
```

**Structure Decision**: KMP multi-module with existing module boundaries. New code distributed across `core/model`, `core/repository`, `core/data`, `core/datastore`, and `feature/settings`. No new Gradle modules needed.

## Complexity Tracking

No constitution violations. All gates pass.
