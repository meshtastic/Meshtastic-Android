# Feature Specification: M3 Expressive Design System Adoption

**Feature Branch**: `20260513-160000-m3-expressive-adoption`  
**Created**: 2026-05-13  
**Status**: Draft  
**Input**: User description: "Adopt Material 3 Expressive design system across the Meshtastic Android app UI, leveraging new typography, animations, component styles, and interaction patterns to create a more dynamic and accessible user experience."  
**Cross-Platform Spec**: N/A — platform-specific only. M3 Expressive APIs are Android/Compose-specific; desktop and iOS targets will receive equivalent improvements via CMP material3 as those APIs stabilize.

## Summary

Adopt the Material 3 Expressive design system throughout the Meshtastic Android app to deliver a more dynamic, accessible, and visually engaging user experience. The app already uses `MaterialExpressiveTheme` and experimental expressive APIs (FAB menus, wavy progress indicators, motion schemes) in isolated places—this feature extends those patterns systematically to every screen and interaction. The primary modules affected are `core/ui` (shared theme and components), all nine `feature/` modules, and `app` (navigation shell).

## Goals

1. **Unified expressive typography**: Replace the default `Typography()` with a custom expressive typescale including emphasized variants and variable font support, providing clear visual hierarchy across all screens
2. **Spring-physics animations everywhere**: Apply the expressive `MotionScheme` consistently so transitions, FAB morphing, navigation indicator movement, and state changes use spring-based physics rather than linear/easing curves
3. **Modernized component library**: Upgrade all 67+ shared components in `core/ui` to use expressive variants where available (FABs, navigation bars, progress indicators, sliders, buttons, lists)
4. **Improved interaction density**: Introduce swipe-to-action on node and message lists, tooltip-labeled FABs, and pill-style navigation indicators to reduce tap-count for common operations
5. **Accessibility gains**: Leverage expressive focus rings, larger touch targets on expressive buttons, and improved motion semantics for reduced-motion users

## Non-Goals

- Migrating the one remaining MDC View usage (F-Droid MapView `MaterialAlertDialogBuilder`) to Compose—that is a separate cleanup task
- Changing the app's color palette or brand identity—the green/neutral/blue Meshtastic scheme remains unchanged
- Introducing new features or screens—this is a design-system upgrade of existing UI
- Supporting M3 Expressive on desktop/iOS targets immediately—those platforms will benefit as CMP material3 stabilizes the APIs
- Redesigning information architecture or navigation structure

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Expressive Navigation Experience (Priority: P1)

A user launches the app and immediately perceives a modern, polished navigation experience with smooth pill-style indicators animating between destinations, spring-physics transitions between screens, and expressive top app bars with refined visual hierarchy.

**Why this priority**: Navigation is the most-used interaction pattern in the app—every user session involves navigating between nodes, messages, map, and settings. Modernizing navigation delivers the broadest perceptual improvement.

**Independent Test**: Can be fully tested by navigating between all primary destinations and verifying smooth indicator animations, spring-based page transitions, and correct top app bar styling. Delivers immediate visual improvement regardless of other stories.

**Acceptance Scenarios**:

1. **Given** the app is launched, **When** the user taps a navigation destination, **Then** the navigation indicator animates to the new position using spring physics (no linear jump)
2. **Given** the user is on any screen with a top app bar, **When** the screen renders, **Then** the app bar uses the expressive style with appropriate typography hierarchy
3. **Given** reduced-motion accessibility setting is enabled, **When** navigating between destinations, **Then** animations are suppressed or use minimal duration per platform accessibility guidelines
4. **Given** a tablet or large-screen device, **When** the app renders navigation, **Then** the navigation rail uses expressive pill-style indicators consistent with the bottom bar on phones

---

### User Story 2 - Expressive Typography and Visual Hierarchy (Priority: P1)

A user reading node details, message threads, or configuration screens perceives clear information hierarchy through expressive typography—emphasized headings, distinct body text, and variable font weight transitions that guide the eye to the most important information.

**Why this priority**: Typography is the primary vehicle for information communication in a mesh-networking app where users must quickly parse node names, signal metrics, message content, and configuration values. Improved hierarchy directly impacts task-completion speed.

**Independent Test**: Can be tested by navigating to any screen and verifying the typescale renders with correct emphasized variants, readable sizes, and consistent hierarchy. Delivers immediate readability improvement.

**Acceptance Scenarios**:

1. **Given** the app renders any screen, **When** a heading-level element appears, **Then** it uses the appropriate emphasized typescale variant (e.g., `titleMediumEmphasized`)
2. **Given** a node detail screen, **When** metrics are displayed, **Then** primary values use display/headline styles and secondary labels use body styles with clear visual separation
3. **Given** the user changes system font size, **When** the app re-renders, **Then** the expressive typescale scales proportionally without layout breakage

---

### User Story 3 - Expressive Component Interactions (Priority: P2)

A user interacting with buttons, FABs, sliders, and progress indicators perceives modern spring-morphing animations, dynamic shapes, and responsive feedback that makes the interface feel alive and responsive.

**Why this priority**: Component-level interactions build engagement and perceived quality. While less critical than navigation and typography (which affect every screen at-a-glance), component expressiveness creates delight during direct manipulation.

**Independent Test**: Can be tested by interacting with FABs (e.g., message compose), sliders (e.g., radio power config), and progress indicators (e.g., firmware update) and verifying spring animations, shape morphing, and expressive feedback.

**Acceptance Scenarios**:

1. **Given** a screen with a FAB (messaging, connections), **When** the user taps the FAB, **Then** it morphs with spring physics and displays a tooltip label on long-press
2. **Given** a firmware update is in progress, **When** the progress indicator renders, **Then** it uses the wavy/expressive progress indicator style with visible amplitude
3. **Given** the radio configuration screen, **When** the user drags a slider (e.g., TX power), **Then** the slider uses expressive styling with smooth spring-based thumb movement
4. **Given** any screen with primary action buttons, **When** buttons render, **Then** they use expressive button styles with appropriate shape rounding

---

### User Story 4 - Expressive List Interactions (Priority: P2)

A user browsing the node list or message list can perform common actions (mute, delete, mark-read, request position) via swipe gestures, reducing the number of taps needed for frequent operations.

**Why this priority**: Swipe-to-action on lists is a power-user accelerator that reduces interaction cost for the most common list operations. It's P2 because it requires careful consideration of discoverability and doesn't affect first-impression quality.

**Independent Test**: Can be tested by swiping on node list items and message items, verifying that appropriate actions appear with correct animations and that executing an action produces the expected result.

**Acceptance Scenarios**:

1. **Given** the node list screen, **When** the user swipes a node item to the right, **Then** a "request position" action is revealed with spring animation
2. **Given** the node list screen, **When** the user swipes a node item to the left, **Then** a "mute node" action is revealed with spring animation
3. **Given** the message list screen, **When** the user swipes a message to the left, **Then** a delete/archive action is revealed
3. **Given** the user completes a swipe action, **When** the action executes, **Then** the list item animates to its new state (removed, updated) with spring physics
4. **Given** a user who has never completed a swipe action in the current session, **When** they first open a list screen, **Then** a subtle edge-peek hint on the first item indicates swipe availability; the hint stops appearing once the user completes a successful swipe

---

### User Story 5 - Expressive Focus and Accessibility (Priority: P3)

A user navigating the app with keyboard, D-pad, or TalkBack perceives clear focus indicators with animated focus rings, appropriate content descriptions, and motion that respects accessibility preferences.

**Why this priority**: Accessibility is always important but ranked P3 because the app already has basic accessibility support. Expressive focus rings and motion-respect are enhancement-level improvements.

**Independent Test**: Can be tested by navigating the app entirely via keyboard/TalkBack, verifying animated focus rings appear on interactive elements and that "reduce motion" setting suppresses spring animations.

**Acceptance Scenarios**:

1. **Given** keyboard navigation is active, **When** focus moves to an interactive element, **Then** an animated expressive focus ring appears around the element
2. **Given** TalkBack is enabled, **When** navigating through expressive components, **Then** all components provide appropriate content descriptions and action labels
3. **Given** system "reduce motion" is enabled, **When** any expressive animation would play, **Then** it either completes instantly or uses a short crossfade instead

---

### Edge Cases

- What happens when the CMP material3 alpha drops a previously-available expressive API in a future version? Wrap experimental APIs behind version-aware expect/actual or feature flags.
- How does the app behave on Android API < 31 where dynamic color is unavailable? The expressive theme uses static color scheme fallback gracefully (already handled).
- What happens when spring animations run on low-end devices? The motion scheme respects system animator duration scale; if set to 0, animations skip.
- How do swipe-to-action gestures interact with the existing drag-and-drop in channel list? Swipe gestures are only enabled on lists without drag-and-drop reordering.

## Architecture

### Key Components

| Component | Module / File | Purpose |
|-----------|---------------|---------|
| `AppTheme` | `core/ui/theme/Theme.kt` | Root theme — already uses `MaterialExpressiveTheme`; extend with full expressive typography and shapes |
| `AppTypography` | `core/ui/theme/Type.kt` | Typography definition — upgrade from default `Typography()` to expressive typescale |
| `MeshtasticNavigationSuite` | `core/ui/component/MeshtasticNavigationSuite.kt` | Adaptive navigation — add expressive pill indicators and spring transitions |
| `MenuFAB` | `core/ui/component/MenuFAB.kt` | Already uses `ExperimentalMaterial3ExpressiveApi` — enhance with tooltip labels and shape morphing |
| `SliderPreference` | `core/ui/component/SliderPreference.kt` | Slider preference — upgrade to expressive slider styling |
| `AlertDialogs` | `core/ui/component/AlertDialogs.kt` | Dialog components — apply expressive shape and motion |
| `MainAppBar` | `core/ui/component/MainAppBar.kt` | Top app bar — apply expressive circle-background nav button style |
| Node list composables | `feature/node/` | Node list — add swipe-to-action with expressive list patterns |
| Message list composables | `feature/messaging/` | Message list — add swipe-to-action and spring item animations |
| Firmware progress | `feature/firmware/` | Already uses `CircularWavyProgressIndicator` — ensure consistent expressive progress across all progress states |
| Connection FABs | `feature/connections/` | Connection action FABs — expressive sizing and tooltip labels |

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The app theme MUST use `MaterialExpressiveTheme` with expressive `MotionScheme` on all screens (extend current partial adoption to full coverage)
- **FR-002**: The typography system MUST define a complete expressive typescale including `displayLargeEmphasized`, `headlineMediumEmphasized`, `titleMediumEmphasized`, `bodyLargeEmphasized`, and `labelLargeEmphasized` variants
- **FR-003**: All navigation indicators (bottom bar, rail) MUST animate between destinations using spring-physics motion
- **FR-004**: All FABs MUST display tooltip labels on long-press and use expressive shape morphing on press
- **FR-005**: All progress indicators MUST use expressive wavy/amplitude styling where indeterminate, and smooth spring-based progress animation where determinate
- **FR-006**: All sliders MUST use expressive styling with spring-based thumb positioning
- **FR-007**: The node list and message list MUST support swipe-to-action gestures with spring-animated action reveal
- **FR-008**: Primary action buttons MUST use expressive button styling with appropriate rounded shapes
- **FR-009**: Focus navigation MUST produce animated focus rings on all interactive elements when keyboard/D-pad input is detected. Note: this is custom UX behavior via `Modifier.indication`, not reimplementation of an existing CMP API (NFR-007 does not apply)
- **FR-010**: All expressive animations MUST respect system "reduce motion" / "animator duration scale" accessibility settings
- **FR-011**: The top app bar MUST use expressive styling consistent with M3 Expressive guidelines
- **FR-012**: Expressive APIs used MUST be wrapped with appropriate `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` annotations and documented as experimental in code comments

### Non-Functional Requirements

- **NFR-001**: No expressive animation shall cause frame drops below 60fps on devices meeting minimum supported API level (API 24) under normal conditions
- **NFR-002**: The expressive theme upgrade shall not increase cold start time by more than 50ms
- **NFR-003**: All expressive components MUST maintain existing accessibility labels and add focus ring support
- **NFR-004**: Expressive API usage MUST be confined to `commonMain` source set where the CMP material3 library provides the API; platform-specific fallbacks in `androidMain` only where necessary
- **NFR-005**: Screenshot tests MUST be updated to capture the new expressive component appearances
- **NFR-006**: Migration MUST be applied as a direct permanent replacement with no runtime or build-time feature flag; rollback is handled via git revert of incremental per-user-story PRs
- **NFR-007**: Expressive APIs not yet available in CMP material3 MUST NOT be manually reimplemented; such features are deferred until the official API ships

## Source-Set Impact

| Source Set | Impact | Justification |
|-----------|--------|---------------|
| `commonMain` | Modified: theme (Type.kt, Theme.kt), all 67 core/ui components, feature module composables | All UI logic and expressive styling lives in commonMain per Constitution §I, §III |
| `androidMain` | Minimal: DynamicColorScheme.kt may need expressive color token mapping | Platform-specific dynamic color integration only |
| `jvmMain` | None expected | Desktop receives expressive styling via commonMain |
| `iosMain` | None expected | iOS receives expressive styling via commonMain |

## Design Standards Compliance

- [x] New screens reviewed against [design standards](https://raw.githubusercontent.com/meshtastic/design/refs/heads/master/standards/meshtastic_design_standards_latest.md)
- [x] M3 component selection verified (e.g., `SwitchPreference` not raw `Switch`)
- [x] Accessibility: TalkBack semantics, touch targets, color-independent info
- [x] Typography: `titleMediumEmphasized` for emphasis, M3 scale for hierarchy

## Privacy Assessment

- [x] No PII, location data, or cryptographic keys logged or exposed
- [x] No new network calls that transmit user data
- [x] Proto submodule (`core/proto`) not modified (read-only upstream)

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of app screens render using `MaterialExpressiveTheme` with no fallback to non-expressive theme
- **SC-002**: Navigation indicator animations complete within 300ms using spring physics (no linear easing detected in animation inspector)
- **SC-003**: Users can complete node-list common actions (request position, mute) in 2 fewer taps via swipe-to-action compared to current menu-based flow
- **SC-004**: Typography hierarchy renders with at least 3 distinct weight/size levels visible per screen (verified via screenshot comparison)
- **SC-005**: All interactive elements produce visible focus rings during keyboard navigation (verified via manual accessibility audit)
- **SC-006**: App cold-start time remains within 50ms of baseline measurement after expressive theme adoption
- **SC-007**: 95% of animated transitions maintain 60fps on reference mid-range device (Pixel 6a or equivalent)
- **SC-008**: Zero accessibility regressions — all existing TalkBack flows continue to function with correct content descriptions

## Clarifications

### Session 2025-07-18

- Q: Should the M3 Expressive migration be gated behind a feature flag allowing rollback, or applied as a direct permanent replacement? → A: Direct replacement — no feature flag; merge incrementally per user story with git-based rollback.
- Q: For pre-Android 12 devices lacking Roboto Flex, should the app bundle the variable font for consistent expressive typography, or accept visual degradation? → A: Accept degradation — pre-Android 12 uses standard weight variants; no font bundled.
- Q: What swipe actions should be available on the node list? → A: Right-swipe = request position, Left-swipe = mute node.
- Q: How should the swipe-to-action discoverability hint behave? → A: Hint repeats each session until user completes a successful swipe action.
- Q: When a CMP material3 expressive API is not yet available, should it be manually reimplemented or skipped? → A: Skip entirely — no manual reimplementations; wait for the official CMP API.

## Assumptions

- All business logic and UI composables reside in `commonMain` source set
- String resources added to `core/resources/src/commonMain/composeResources/values/strings.xml`
- Icons use `MeshtasticIcons` (from `core/ui/icon/`)
- Float values pre-formatted with `NumberFormatter.format()` (CMP constraint)
- The CMP material3 library version 1.11.0-alpha07 (backed by Jetpack Compose Material3 ~1.5.0-alpha17) provides sufficient expressive API surface for typography, motion scheme, FAB, and progress indicators; APIs not yet available in CMP are skipped entirely (no manual reimplementations) and adopted when the official API ships
- The `ExperimentalMaterial3ExpressiveApi` annotation will remain available and not be removed in near-term CMP updates (based on current Jetpack Compose trajectory)
- Swipe-to-action gesture implementation can leverage Compose Foundation gesture APIs without requiring platform-specific code
- Expressive typography will use system-available variable fonts (no custom font bundling); Roboto Flex is the M3 reference font available on Android 12+
- On pre-Android 12 devices, expressive typography gracefully degrades to standard weight variants without visual breakage; no font is bundled to cover this gap (APK size preserved)
- The existing screenshot test infrastructure (screenshot-tests/ directory) will be used to validate visual changes
