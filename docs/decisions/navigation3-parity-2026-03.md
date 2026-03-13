<!--
 - Copyright (c) 2026 Meshtastic LLC
 -
 - This program is free software: you can redistribute it and/or modify
 - it under the terms of the GNU General Public License as published by
 - the Free Software Foundation, either version 3 of the License, or
 - (at your option) any later version.
 -->

# Navigation 3 Parity Strategy (Android + Desktop)

**Date:** 2026-03-11
**Status:** Active
**Scope:** `app` and `desktop` navigation structure using shared `core:navigation` routes

## Context

Desktop and Android both use Navigation 3 typed routes from `core:navigation`. Previously graph wiring had diverged — desktop used a separate `DesktopDestination` enum with 6 entries (including a top-level Firmware tab) while Android used 5 entries.

This has been resolved. Both shells now use the shared `TopLevelDestination` enum from `core:navigation/commonMain` with 5 entries (Conversations, Nodes, Map, Settings, Connections). Firmware is an in-flow route on both platforms.

Both modules still define separate graph-builder files (`app/navigation/*.kt`, `desktop/navigation/*.kt`) with different destination coverage and placeholder behavior, but the **top-level shell structure is unified**.

## Current-State Findings

1. **Top-level destinations are unified.**
   - Both shells iterate `TopLevelDestination.entries` from `core:navigation/commonMain`.
   - Shared icon mapping lives in `core:ui` (`TopLevelDestinationExt.icon`).
   - Parity tests exist in both `core:navigation/commonTest` (`NavigationParityTest`) and `desktop/test` (`DesktopTopLevelDestinationParityTest`).
2. **Feature coverage differs by intent and by implementation.**
   - Desktop intentionally uses placeholders for map and several node/message detail flows.
   - Android wires real implementations for map, message/share flows, and more node detail paths.
3. **Saved-state route registration is desktop-only and manual.**
   - `DesktopMainScreen.kt` maintains a large `SavedStateConfiguration` serializer list that must stay in sync with `Routes.kt` and desktop graph entries.
4. **Route keys are shared; graph registration is per-platform.**
   - This is the expected state — platform shells wire entries differently while consuming the same route types.

## Alpha04 Changelog Impact Check (2026-03-13)

Source reviewed: Compose Multiplatform `v1.11.0-alpha04` release notes.

1. **No direct Navigation 3 API breakage called out.**
   - Release notes include component version bumps for Navigation 3 (`1.1.0-alpha04`) but no `NavBackStack`, `NavDisplay`, or `entryProvider` API migration requirements.
   - Existing shell patterns in `app` and `desktop` remain valid.
2. **Primary risk is dependency wiring drift, not runtime behavior.**
   - JetBrains Navigation 3 currently publishes `navigation3-ui` coordinates (no separate `navigation3-runtime` artifact in Maven Central). Our `androidx-navigation3-runtime` alias intentionally points to `navigation3-ui` and is now explicitly documented in the version catalog.
3. **Saved-state and typed-route parity risk remains unchanged.**
   - Desktop still uses manual serializer registration; this is an existing risk and not introduced by alpha04.
4. **Compose-wide migration notes do not currently impact navigation codepaths.**
   - `Shader` wrapper changes and `Canvas.nativeCanvas` deprecations are not used in the Navigation 3 shell files.

### Actions Taken

- Documented in the version catalog that `androidx-navigation3-runtime` intentionally maps to `navigation3-ui` until a separate runtime artifact is published.
- Updated active docs to reflect the current dependency baseline (`1.11.0-alpha04`, `1.1.0-alpha04`, `1.3.0-alpha06`, `2.10.0-beta01`).
- Consolidated `app` adaptive dependencies to JetBrains Material 3 Adaptive coordinates (`org.jetbrains.compose.material3.adaptive:*`) so Android and Desktop consume the same adaptive artifact family. The Android-only navigation suite remains on `androidx.compose.material3:material3-adaptive-navigation-suite`.

### Deferred Follow-ups

- Add automated validation that desktop serializer registrations stay in sync with shared route keys.

## Options Evaluated

### Option A: Reuse `:app` navigation implementation directly in desktop

**Pros**
- Maximum short-term parity in structure.

**Cons**
- `:app` graph code is tightly coupled to Android wrappers (`Android*ViewModel`, Android-only screen wrappers, app-specific UI state like scroll-to-top flows).
- Pulling this code into desktop would either fail at compile-time or force additional platform branching in app files.
- Violates clean module boundaries (`desktop` should not depend on Android-specific app glue).

**Decision:** Not recommended.

### Option B: Keep fully separate desktop graph and replicate app behavior manually

**Pros**
- Lowest refactor cost right now.
- Keeps platform customization simple.

**Cons**
- Drift is guaranteed over time.
- No central policy for intentional vs accidental divergence.
- High maintenance burden for parity-sensitive flows.

**Decision:** Not recommended as a long-term strategy.

### Option C (Recommended): Hybrid shared contract + platform graph adapters

**Pros**
- Preserves platform-specific wiring where needed.
- Reduces drift by moving parity-sensitive definitions to shared contracts.
- Enables explicit, testable exceptions for desktop-only or Android-only behavior.

**Cons**
- Requires incremental extraction work.
- Needs light governance (parity matrix + tests + docs).

**Decision:** Recommended.

## Decision

Adopt a **hybrid parity model**:

1. Keep platform graph registration in `app` and `desktop`.
2. Extract parity-sensitive navigation metadata into shared contracts (top-level destination set/order, route ownership map, and allowed platform exceptions).
3. Keep platform-specific destination implementations as adapters around shared route keys.
4. Add route parity tests so drift is detected automatically.

## Implementation Plan

### Phase 1 (Immediate): Stop drift on shell structure ✅

- ✅ Aligned desktop top-level destination policy with Android (removed Firmware from top-level; kept as in-flow).
- ✅ Both shells now use shared `TopLevelDestination` enum from `core:navigation/commonMain`.
- ✅ Shared icon mapping in `core:ui` (`TopLevelDestinationExt.icon`).
- Parity matrix documented inline: top-level set is Conversations, Nodes, Map, Settings, Connections on both platforms.

### Phase 2 (Near-term): Extract shared navigation contracts ✅ (partially)

- ✅ Shared `TopLevelDestination` enum with `fromNavKey()` already serves as the canonical metadata object.
- Both `app` and `desktop` shells iterate `TopLevelDestination.entries` — no separate `DesktopDestination` enum remains.
- Remaining: optional visibility flags by platform, route grouping metadata (lower priority since shells are unified).

### Phase 3 (Near-term): Add parity checks ✅ (partially)

- ✅ `NavigationParityTest` in `core:navigation/commonTest` — asserts 5 top-level destinations and `fromNavKey` matching.
- ✅ `DesktopTopLevelDestinationParityTest` in `desktop/test` — asserts desktop routes match Android parity set and firmware is not top-level.
- Remaining: assert every desktop serializer registration corresponds to an actual route; assert every intentional exception is listed.

### Phase 4 (Mid-term): Reduce app-specific graph coupling

- Move reusable graph composition helpers out of `:app` where practical (while keeping Android-only wrappers in Android source sets).
- Keep desktop-specific placeholder implementations, but tie them to explicit parity exception entries.

## Consequences

- Navigation behavior remains platform-adaptive, but parity expectations become explicit and enforceable.
- Desktop can keep legitimate deviations (map/charts/platform integrations) without silently changing IA.
- New route additions will require touching one shared contract plus platform implementations, making review scope clearer.

## Source Anchors

- Shared routes: `core/navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/Routes.kt`
- Android shell: `app/src/main/kotlin/org/meshtastic/app/ui/Main.kt`
- Android graph registrations: `app/src/main/kotlin/org/meshtastic/app/navigation/`
- Desktop shell: `desktop/src/main/kotlin/org/meshtastic/desktop/ui/DesktopMainScreen.kt`
- Desktop graph registrations: `desktop/src/main/kotlin/org/meshtastic/desktop/navigation/`


