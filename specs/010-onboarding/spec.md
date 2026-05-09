# Feature Specification: Onboarding / Intro Flow

**Feature Branch**: `010-onboarding`  
**Created**: 2026-05-09  
**Status**: Migrated  
**Input**: Brownfield migration — reverse-engineered from existing `feature/intro` module

## Summary

The onboarding intro flow provides a first-run experience that welcomes new users, explains the app's key capabilities (off-grid messaging, mesh networking, location sharing), and guides them through granting the runtime permissions required for Meshtastic to function: Bluetooth, Location, Notifications, and Critical Alerts (Do Not Disturb override). The flow is a linear wizard driven by Navigation 3 with skip/configure actions on each step.

## Goals

1. **Guide first-time users** through a clear, sequential introduction to the app's value proposition and required permissions.
2. **Request runtime permissions** (Bluetooth, Location, Notifications) in context, explaining *why* each is needed before prompting.
3. **Support graceful degradation** — users can skip any permission step and still complete onboarding.
4. **Handle API-level differences** — adapt the permission set dynamically for Android 12+ (BLE permissions) and Android 13+ (POST_NOTIFICATIONS).
5. **Provide a pathway to system Settings** when permissions have been previously denied and must be granted manually.

## Non-Goals

- Persisting "intro completed" state (handled by the caller / app-level DataStore).
- Device pairing or mesh configuration — this is purely the permission onboarding wizard.
- Supporting iOS or Desktop targets — UI screens are currently Android-only (`androidMain`).
- Analytics collection — analytics opt-in is surfaced via `LocalAnalyticsIntroProvider` but not owned by this feature.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Welcome & Value Proposition (Priority: P1)

A first-time user opens the app and sees a branded welcome screen that highlights three key features: off-grid communication, private mesh networks, and location sharing. The user taps "Get Started" to begin the setup wizard.

**Why this priority**: This is the entry point — without it, no other onboarding step is reachable.

**Independent Test**: Render the `WelcomeScreen`, verify three feature rows are displayed, and verify tapping "Get Started" triggers the `onGetStarted` callback.

**Acceptance Scenarios**:

1. **Given** the app is launched for the first time, **When** the intro flow starts, **Then** the Welcome screen is displayed with "Welcome to" heading, "Meshtastic" title, and three feature rows (connectivity, networks, location).
2. **Given** the Welcome screen is displayed, **When** the user taps "Get Started", **Then** the app navigates to the Bluetooth permission screen.
3. **Given** the Welcome screen is displayed, **Then** no "Skip" button is visible — the only action is "Get Started".

---

### User Story 2 — Bluetooth Permission Grant (Priority: P1)

The user is presented with an explanation of why Bluetooth is needed (device discovery & configuration). They can grant the permission, skip, or navigate to system Settings if the permission was previously denied.

**Why this priority**: Bluetooth is the core transport for Meshtastic; the app is largely non-functional without it.

**Independent Test**: Render `BluetoothScreen` with `showNextButton=false`, verify feature rows and button text, simulate configure tap.

**Acceptance Scenarios**:

1. **Given** the Bluetooth screen is displayed and permissions are NOT granted, **When** the user taps "Configure Bluetooth Permissions", **Then** the system permission dialog is launched.
2. **Given** the Bluetooth screen is displayed and permissions ARE already granted, **When** the screen renders, **Then** the button text changes to "Next" and tapping it navigates to Location.
3. **Given** the Bluetooth screen is displayed, **When** the user taps "Skip", **Then** the app navigates to the Location screen without granting permissions.
4. **Given** the description text contains a "Settings" link, **When** the user taps it, **Then** the app opens the system Application Details settings page.

---

### User Story 3 — Location Permission Grant (Priority: P1)

The user is shown why location access is beneficial (share location, distance measurements, distance filters, mesh map). They can grant, skip, or open Settings.

**Why this priority**: Location is essential for map features and position sharing — a core Meshtastic use case.

**Independent Test**: Render `LocationScreen`, verify four feature rows, simulate permission grant flow.

**Acceptance Scenarios**:

1. **Given** the Location screen is displayed and permissions are NOT granted, **When** the user taps "Configure Location Permissions", **Then** the system permission dialog (fine + coarse) is launched.
2. **Given** the Location screen is displayed and permissions ARE already granted, **Then** the button text is "Next".
3. **Given** the user taps "Skip", **Then** the app navigates to the Notifications screen.

---

### User Story 4 — Notification Permission Grant (Priority: P2)

The user is shown why notifications are valuable (incoming messages, new nodes, low battery alerts). On Android 13+ the system dialog is shown; on older versions the step auto-advances.

**Why this priority**: Notifications enhance the experience but the app is still usable without them.

**Independent Test**: Render `NotificationsScreen`, verify three feature rows, test both API 33+ and pre-33 code paths.

**Acceptance Scenarios**:

1. **Given** Android 13+ and notification permission is NOT granted, **When** the user taps "Configure Notification Permissions", **Then** the POST_NOTIFICATIONS permission dialog is launched.
2. **Given** Android < 13 (no runtime notification permission), **When** the Notifications screen renders, **Then** the button shows "Next" and proceeds to CriticalAlerts.
3. **Given** the user taps "Skip" on the Notifications screen, **Then** `onDone` is invoked and the intro flow ends (no CriticalAlerts step).
4. **Given** notification permission is granted, **When** the user taps "Next", **Then** the app navigates to the CriticalAlerts screen.

---

### User Story 5 — Critical Alerts / DND Configuration (Priority: P3)

The user is informed about critical alerts that can bypass Do Not Disturb. They can configure the notification channel settings or skip.

**Why this priority**: This is an advanced preference; most users can safely skip it.

**Independent Test**: Render `CriticalAlertsScreen`, verify heading and description text, verify "Configure" opens system notification channel settings.

**Acceptance Scenarios**:

1. **Given** the CriticalAlerts screen is displayed, **When** the user taps "Configure Critical Alerts", **Then** the system notification channel settings Intent is launched and `onDone` is called.
2. **Given** the CriticalAlerts screen is displayed, **When** the user taps "Skip", **Then** `onDone` is called and the intro flow ends.

---

### Edge Cases

- What happens when the user rotates the device mid-wizard? → Navigation 3 backstack survives configuration changes via `rememberNavBackStack`.
- What happens on pre-Android-12 devices where BLE permissions don't exist? → `bluetoothPermissions` list is empty; the Bluetooth screen still appears with skip/next.
- What happens if the user presses back? → Navigation 3 backstack handles back navigation to the previous intro step.
- What happens if `createClickableAnnotatedString` fails to find the "Settings" substring? → The annotation is silently skipped (no crash); the text renders as plain text.

## Architecture

### Key Components

| Component | Module / File | Purpose |
|-----------|---------------|---------|
| `IntroViewModel` | `feature/intro/src/commonMain/.../IntroViewModel.kt` | Determines the next navigation key based on current step and permission state |
| `IntroNavKeys` | `feature/intro/src/commonMain/.../IntroNavKeys.kt` | `@Serializable data object` navigation keys: Welcome, Bluetooth, Location, Notifications, CriticalAlerts |
| `FeatureIntroModule` | `feature/intro/src/commonMain/.../di/FeatureIntroModule.kt` | Koin DI module with `@ComponentScan` |
| `AppIntroductionScreen` | `feature/intro/src/androidMain/.../AppIntroductionScreen.kt` | Root composable — hoists permission states and wires nav graph |
| `introNavGraph` | `feature/intro/src/androidMain/.../IntroNavGraph.kt` | Navigation 3 entry provider with per-screen permission handling |
| `PermissionScreenLayout` | `feature/intro/src/androidMain/.../PermissionScreenLayout.kt` | Reusable layout for permission screens (headline, description, features, bottom bar) |
| `IntroBottomBar` | `feature/intro/src/androidMain/.../IntroBottomBar.kt` | Skip / Configure bottom bar used across all intro screens |
| `FeatureUIData` | `feature/intro/src/androidMain/.../FeatureUIData.kt` | Data class for feature row icon + title + subtitle |
| `FeatureRow` | `feature/intro/src/androidMain/.../IntroUiHelpers.kt` | Composable row displaying icon, title, subtitle |
| `createClickableAnnotatedString` | `feature/intro/src/androidMain/.../IntroUiHelpers.kt` | Builds annotated strings with clickable "Settings" links |
| `MeshtasticNavDisplay` | `core/ui/component/` | Shared navigation display wrapper (reused) |
| `MeshtasticIcons` | `core/ui/icon/` | Project icon set (reused) |

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST display a Welcome screen with app branding and three feature highlights on first launch.
- **FR-002**: System MUST present permission screens in fixed order: Bluetooth → Location → Notifications → Critical Alerts.
- **FR-003**: Each permission screen MUST provide a "Skip" button allowing the user to proceed without granting the permission.
- **FR-004**: Each permission screen MUST show a "Configure" button that launches the appropriate system permission dialog.
- **FR-005**: When a permission is already granted, the configure button MUST change to "Next" and skip the system dialog.
- **FR-006**: System MUST adapt the Bluetooth permission set based on API level: `BLUETOOTH_SCAN` + `BLUETOOTH_CONNECT` on Android 12+, empty list on older versions.
- **FR-007**: System MUST only request `POST_NOTIFICATIONS` on Android 13+; on older versions, the Notifications step auto-advances.
- **FR-008**: Each permission screen description MUST contain a clickable "Settings" link that opens the app's system Settings page.
- **FR-009**: The CriticalAlerts "Configure" action MUST open the system notification channel settings with channel ID `"my_alerts"`.
- **FR-010**: Skipping the Notifications screen MUST end the intro flow immediately (no CriticalAlerts step).

### Non-Functional Requirements

- **NFR-001**: All navigation logic (`IntroViewModel.getNextKey`) MUST reside in `commonMain` and be unit-testable without Android framework dependencies.
- **NFR-002**: All user-visible strings MUST use `stringResource(Res.string.*)` from `core:resources` — no hardcoded UI strings.
- **NFR-003**: Icons MUST use `MeshtasticIcons` from `core/ui/icon/`.
- **NFR-004**: The intro flow MUST be scrollable to accommodate small screens and large font sizes.

## Source-Set Impact

| Source Set | Impact | Justification |
|-----------|--------|---------------|
| `commonMain` | 3 files — ViewModel, NavKeys, DI module | Navigation logic and DI wiring (Constitution §I compliant) |
| `androidMain` | 8 files — all UI screens & helpers | Permission APIs require `android.*` imports (Accompanist, Intent, Build.VERSION) |
| `jvmMain` | None | N/A |

## Design Standards Compliance

- [x] New screens reviewed against [design standards](https://raw.githubusercontent.com/meshtastic/design/refs/heads/master/standards/meshtastic_design_standards_latest.md)
- [x] M3 component selection verified (`Scaffold`, `BottomAppBar`, `Button`, `Text` with M3 Typography)
- [ ] Accessibility: TalkBack semantics, touch targets, color-independent info — not explicitly verified
- [x] Typography: `headlineLarge` for headlines, `titleMedium` with `SemiBold` for feature titles, `bodyLarge`/`bodyMedium` for descriptions

## Privacy Assessment

- [x] No PII, location data, or cryptographic keys logged or exposed
- [x] No new network calls that transmit user data
- [x] Proto submodule (`core/proto`) not modified (read-only upstream)

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: All 6 ViewModel navigation tests pass (`IntroViewModelTest`) covering the full step sequence and branching logic.
- **SC-002**: The intro flow renders 5 distinct screens (Welcome, Bluetooth, Location, Notifications, CriticalAlerts) with correct content.
- **SC-003**: Users can complete the entire flow (granting all permissions) or skip every step — both paths invoke `onDone`.
- **SC-004**: On Android 12+ devices, Bluetooth permission dialog is triggered; on older devices, the step is skippable.
- **SC-005**: On Android 13+ devices, notification permission dialog is triggered; on older devices, the step auto-advances.
- **SC-006**: The "Settings" deep link in permission descriptions opens the correct system screen.

## Assumptions

- All business logic and UI composables reside in `commonMain` source set (ViewModel and NavKeys do; UI screens are in `androidMain` — see Gaps).
- String resources added to `core/resources/src/commonMain/composeResources/values/strings.xml`.
- Icons use `MeshtasticIcons` (from `core/ui/icon/`).
- The caller (app module) is responsible for persisting "intro completed" state and conditionally showing the intro flow.
- `LocalAnalyticsIntroProvider` is provided by the app module's composition local, not by this feature.
- The notification channel `"my_alerts"` is pre-created by the app module's notification setup code.

