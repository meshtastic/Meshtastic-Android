# Implementation Plan: M3 Expressive Design System Adoption

**Branch**: `20260513-160000-m3-expressive-adoption` | **Date**: 2026-05-13 | **Spec**: `specs/20260513-160000-m3-expressive-adoption/spec.md`  
**Input**: Feature specification from `specs/20260513-160000-m3-expressive-adoption/spec.md`

## Summary

Adopt Material 3 Expressive design system fully across the Meshtastic Android app by extending the existing partial `MaterialExpressiveTheme` + `MotionScheme.expressive()` to all screens and components. Key deliverables: complete expressive typography with emphasized variants, spring-physics navigation indicators, upgraded core/ui component library (67+ composables), swipe-to-action on node/message lists (right=request position, left=mute), and accessibility focus ring support. Implementation uses only APIs available in CMP material3 1.11.0-alpha07 — no manual reimplementations, no feature flags, no font bundling.

## Technical Context

**Language/Version**: Kotlin 2.3+ / JDK 21 / KMP  
**Primary Dependencies**: CMP material3 `1.11.0-alpha07`, Compose Foundation (gestures), Compose Animation (spring)  
**Storage**: DataStore (single boolean preference for swipe hint dismissal)  
**Testing**: `./gradlew allTests` (KMP), `compose-screenshot 0.0.1-alpha14` (visual regression)  
**Target Platform**: Android API 24+, Compose Desktop, iOS (via commonMain)  
**Project Type**: KMP mobile/desktop app  
**Performance Goals**: 60fps animations on mid-range devices (Pixel 6a), cold-start ≤ 50ms regression  
**Constraints**: No APK size increase (no font bundling), no runtime feature flags, commonMain-only UI code  
**Scale/Scope**: 67+ core/ui components, 9 feature modules, ~50 screens

## Constitution Check

*GATE: ✅ PASSED (pre-design) — Re-checked post-design below.*

- **I. Kotlin Multiplatform Core**: ✅ All changes are in `commonMain` source sets. Modules touched: `core/ui`, `core/datastore` (1 boolean pref), all 9 `feature/*` modules. No new `androidMain` code. The only existing `androidMain` code (DynamicColorScheme.kt) is unchanged.

- **II. Zero Lint Tolerance**: ✅ Verification commands:
  ```bash
  ./gradlew spotlessApply spotlessCheck detekt
  # Per-module: :core:ui:detekt :feature:node:detekt etc.
  ```
  All touched modules will pass `spotlessCheck` + `detekt` before push.

- **III. Compose Multiplatform UI**: ✅ All UI uses Compose Multiplatform patterns. Navigation remains `MeshtasticNavDisplay` + `NavigationBackHandler`. No floats displayed (typography/animation values are internal). New `SwipeToRevealBox` composable uses only Compose Foundation APIs available in CMP.

- **IV. Privacy First**: ✅ No PII, location data, or keys logged. No new network calls. `core/proto` untouched. The only new preference (`hasCompletedSwipeAction`) stores no sensitive data.

- **V. Design Standards Compliance**: ✅ Verified against design standards v1.4:
  - Typography: `bodyMedium/bodyLarge >= 16sp` (§5 minimum)
  - Color: No palette changes; M3 role mapping (§8) unchanged
  - Accessibility: 4.5:1 contrast maintained, 44dp touch targets, 200% Dynamic Type
  - Cross-platform spec: N/A — M3 Expressive is platform-specific styling layer. The `meshtastic/design/features/` directory does not exist. Justification: this is an implementation-layer styling upgrade, not a UX behavior change.

- **VI. Verify Before Push**: ✅ Commands:
  ```bash
  ./gradlew spotlessApply detekt assembleDebug test allTests
  # Post-push:
  gh pr checks <PR> || gh run list --branch 20260513-160000-m3-expressive-adoption --limit 5
  ```

### Post-Design Re-Check

✅ All six principles remain satisfied after Phase 1 design:
- New `SwipeToRevealBox` is in `commonMain` (Principle I ✅)
- No lint exceptions introduced (Principle II ✅)
- Uses CMP Foundation gestures, not Android-specific (Principle III ✅)
- DataStore pref `hasCompletedSwipeAction` is non-sensitive (Principle IV ✅)
- Typography/component changes maintain design standards compliance (Principle V ✅)
- Build commands documented above (Principle VI ✅)

## Project Structure

### Documentation (this feature)

```text
specs/20260513-160000-m3-expressive-adoption/
├── plan.md                              # This file
├── research.md                          # Phase 0: technology decisions
├── data-model.md                        # Phase 1: entities and state models
├── quickstart.md                        # Phase 1: developer onboarding
├── contracts/
│   ├── swipe-to-reveal-api.md           # SwipeToRevealBox public API contract
│   └── expressive-typography-api.md     # Typography usage guidelines
└── tasks.md                             # Phase 2: implementation tasks (pending)
```

### Source Code (repository root)

```text
core/
├── ui/src/commonMain/.../theme/
│   ├── Theme.kt          # MaterialExpressiveTheme (exists, extend)
│   ├── Type.kt           # AppTypography (exists, upgrade)
│   └── Color.kt          # Color tokens (unchanged)
├── ui/src/commonMain/.../component/
│   ├── SwipeToRevealBox.kt        # NEW: swipe-to-action container
│   ├── MeshtasticNavigationSuite.kt  # MODIFY: expressive indicators
│   ├── MainAppBar.kt              # MODIFY: expressive styling
│   ├── MenuFAB.kt                 # MODIFY: tooltip + shape
│   ├── SliderPreference.kt        # MODIFY: spring animation
│   ├── AlertDialogs.kt            # MODIFY: expressive shape/motion
│   └── [63 other components]      # MODIFY: typography references
├── datastore/src/commonMain/...
│   └── UserPreferences            # MODIFY: add hasCompletedSwipeAction

feature/
├── node/src/commonMain/.../list/
│   └── NodeListScreen.kt          # MODIFY: integrate SwipeToRevealBox
├── messaging/src/commonMain/...
│   └── [message list screen]      # MODIFY: integrate SwipeToRevealBox
├── firmware/src/commonMain/...    # MODIFY: wavy progress consistency
├── connections/src/commonMain/... # MODIFY: FAB tooltip labels
└── [5 other modules]              # MODIFY: typography + component updates

screenshot-tests/
└── src/screenshotTest/...         # MODIFY: update reference images
```

**Structure Decision**: Existing KMP multi-module architecture. No new modules created. One new file (`SwipeToRevealBox.kt`) in `core/ui/component/`. One new DataStore field. All other work is modifications to existing files.

## Complexity Tracking

> No Constitution violations. No complexity exceptions needed.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |
