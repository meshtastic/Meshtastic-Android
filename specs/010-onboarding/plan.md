# Implementation Plan: Onboarding / Intro Flow

**Branch**: `010-onboarding` | **Date**: 2026-05-09 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/010-onboarding/spec.md`
**Status**: Migrated — reverse-engineered from existing implementation

## Summary

The onboarding intro flow is a 5-step linear wizard (Welcome → Bluetooth → Location → Notifications → Critical Alerts) that introduces first-time users to Meshtastic and requests runtime permissions. Navigation logic lives in `commonMain` via `IntroViewModel`; UI screens and platform permission handling live in `androidMain` using Accompanist Permissions and Navigation 3.

## Technical Context

**Language/Version**: Kotlin 2.3+ targeting JDK 21  
**Primary Dependencies**: Compose Multiplatform, Material 3, Koin 4.2+ (K2 Compiler Plugin), Accompanist Permissions, Navigation 3  
**Storage**: N/A (no local persistence — intro completion state managed by caller)  
**Testing**: KMP `allTests` for `feature:intro` module  
**Target Platform**: Android (UI), Desktop/iOS (commonMain logic only)  
**Project Type**: Mobile/desktop app (Kotlin Multiplatform)  
**Performance Goals**: Instant screen transitions; no network calls  
**Constraints**: All UI in `androidMain` due to Accompanist Permissions dependency; ViewModel logic in `commonMain`  
**Scale/Scope**: 3 `commonMain` files, 8 `androidMain` files, 1 test file — ~1,150 total lines

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Kotlin Multiplatform Core | ✅ PASS | ViewModel and NavKeys in `commonMain`. UI screens in `androidMain` due to platform permission APIs — acceptable per source-set boundaries. |
| II. Zero Lint Tolerance | ✅ PASS | Module compiles with `spotlessCheck` and `detekt` passing. |
| III. Compose Multiplatform UI | ✅ PASS | Uses `MeshtasticNavDisplay`, Navigation 3 `entryProvider`, `@Serializable data object` NavKeys. |
| IV. Privacy First | ✅ PASS | No PII logged. No network calls. Proto submodule untouched. |
| V. Design Standards Compliance | ✅ PASS | M3 Typography, `Scaffold`, `BottomAppBar` used throughout. `MeshtasticIcons` for all icons. |
| VI. Verify Before Push | ✅ PASS | `./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests` passes. |
| VII. Coroutine Safety | ✅ PASS | No coroutine/suspend code in this feature — ViewModel is synchronous. |
| VIII. Resource Discipline | ✅ PASS | All strings via `stringResource(Res.string.*)`. All icons via `MeshtasticIcons`. |
| IX. Branch & Scope Hygiene | ✅ PASS | Feature is self-contained in `feature/intro` module with clear boundaries. |

**Gate Result**: ✅ All principles satisfied

## Project Structure

### Documentation (this feature)

```text
specs/010-onboarding/
├── spec.md              # Feature specification (migrated)
├── plan.md              # This file (migrated)
└── tasks.md             # Task breakdown (migrated)
```

### Source Code (repository root)

```text
feature/intro/                                    ← Primary module
├── build.gradle.kts                              ← KMP feature plugin + serialization
├── src/commonMain/kotlin/org/meshtastic/feature/intro/
│   ├── IntroNavKeys.kt                           ← @Serializable NavKey data objects
│   ├── IntroViewModel.kt                         ← Navigation step logic (getNextKey)
│   └── di/
│       └── FeatureIntroModule.kt                 ← Koin @Module with @ComponentScan
├── src/androidMain/kotlin/org/meshtastic/feature/intro/
│   ├── AppIntroductionScreen.kt                  ← Root composable, permission state hoisting
│   ├── IntroNavGraph.kt                          ← Navigation 3 entry provider
│   ├── WelcomeScreen.kt                          ← Welcome step with feature highlights
│   ├── BluetoothScreen.kt                        ← Bluetooth permission step
│   ├── LocationScreen.kt                         ← Location permission step
│   ├── NotificationsScreen.kt                    ← Notification permission step
│   ├── CriticalAlertsScreen.kt                   ← DND / critical alerts step
│   ├── PermissionScreenLayout.kt                 ← Reusable permission screen scaffold
│   ├── IntroBottomBar.kt                         ← Skip/Configure bottom bar
│   ├── IntroUiHelpers.kt                         ← FeatureRow + clickable annotated strings
│   └── FeatureUIData.kt                          ← Data class for feature row content
└── src/commonTest/kotlin/org/meshtastic/feature/intro/
    └── IntroViewModelTest.kt                     ← 6 navigation flow tests

core/ui/component/MeshtasticNavDisplay.kt         ← Reused — navigation display wrapper
core/ui/icon/MeshtasticIcons.kt                   ← Reused — project icon set
core/resources/src/commonMain/composeResources/    ← Reused — string resources
```

**Structure Decision**: The feature follows the standard `feature/*` KMP module pattern. UI screens are in `androidMain` because they depend on Accompanist Permissions and Android Intent APIs. The ViewModel and navigation keys are correctly in `commonMain` for cross-platform reuse.

## Module Impact

| Module | Change Type | Files Affected | Risk |
|--------|-------------|----------------|------|
| `feature/intro` | Existing (complete) | 12 source + 1 test | Low |
| `core/ui` | Reused (no changes) | 0 | None |
| `core/resources` | Reused (strings added) | 1 (strings.xml) | Low |

## Integration Points

- **App-level routing**: The app module (or root navigation) conditionally presents `AppIntroductionScreen` on first launch and handles the `onDone` callback to persist completion and navigate to the main app.
- **Koin DI**: `FeatureIntroModule` is registered in the app's Koin configuration. `IntroViewModel` is injected via `@KoinViewModel`.
- **Analytics**: `LocalAnalyticsIntroProvider` composition local is provided by the app module; the Welcome screen invokes it for opt-in display.
- **Notification Channel**: The `"my_alerts"` channel ID is referenced by the CriticalAlerts screen but must be pre-created elsewhere.

## Design Constraints

- All UI lives in `androidMain` — platform permission APIs require it
- Strings accessed via `stringResource(Res.string.key)` — never hardcoded
- Icons use `MeshtasticIcons` exclusively (from `core/ui/icon/`)
- No coroutine code — ViewModel is purely synchronous
- Navigation uses Navigation 3 `entryProvider` pattern with `rememberNavBackStack`
- Permission screens adapt button text based on grant state (`showNextButton` flag)

## Risk Assessment

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| UI screens can't compile on Desktop/iOS | High | Medium | Screens are in `androidMain`; only ViewModel is cross-platform. Migration to CMP permissions API is a future task. |
| Notification channel `"my_alerts"` not found | Low | Low | Channel must be created by app module; verify in integration test. |
| Accompanist Permissions deprecated | Medium | Medium | Google has signaled Accompanist may be absorbed into core; monitor and migrate when alternatives are stable. |

## Phase Alignment with Tasks

| Phase | Purpose | Key Tasks | Dependencies |
|-------|---------|-----------|--------------|
| 1. Core Model & Navigation | NavKeys + ViewModel + DI | OB-T001 – OB-T003 | None |
| 2. UI Screens | All 5 screens + shared layout | OB-T004 – OB-T011 | Phase 1 |
| 3. Testing | ViewModel unit tests | OB-T012 – OB-T013 | Phases 1–2 |

### Critical Path

```
Phase 1 (NavKeys, ViewModel, DI) → Phase 2 (UI Screens) → Phase 3 (Tests)
```

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| *None* | — | — |

