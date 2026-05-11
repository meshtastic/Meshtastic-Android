# Tasks: Onboarding / Intro Flow

**Spec**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)  
**Status**: Migrated  
**Prefix**: OB-T

---

## Phase 1 — Core Model & Navigation

- [x] **OB-T001**: Define `@Serializable data object` NavKeys (Welcome, Bluetooth, Location, Notifications, CriticalAlerts) implementing `NavKey`
  - File: `feature/intro/src/commonMain/kotlin/org/meshtastic/feature/intro/IntroNavKeys.kt`
  - Acceptance: All 5 keys compile and are serializable

- [x] **OB-T002**: Implement `IntroViewModel` with `getNextKey(currentKey, allPermissionsGranted)` navigation logic
  - File: `feature/intro/src/commonMain/kotlin/org/meshtastic/feature/intro/IntroViewModel.kt`
  - Acceptance: Welcome→Bluetooth→Location→Notifications→CriticalAlerts (or null) flow works; branching at Notifications based on permission state

- [x] **OB-T003**: Create Koin DI module with `@Module` + `@ComponentScan`
  - File: `feature/intro/src/commonMain/kotlin/org/meshtastic/feature/intro/di/FeatureIntroModule.kt`
  - Acceptance: `IntroViewModel` injectable via `@KoinViewModel`

---

## Phase 2 — UI Screens

- [x] **OB-T004**: Implement `FeatureUIData` data class (icon, titleRes, subtitleRes)
  - File: `feature/intro/src/androidMain/kotlin/org/meshtastic/feature/intro/FeatureUIData.kt`
  - Acceptance: Data class holds `ImageVector` + optional `StringResource` title + required subtitle

- [x] **OB-T005**: Implement `FeatureRow` composable and `createClickableAnnotatedString` helper
  - File: `feature/intro/src/androidMain/kotlin/org/meshtastic/feature/intro/IntroUiHelpers.kt`
  - Acceptance: Feature rows render icon + title + subtitle; annotated strings have clickable "Settings" link

- [x] **OB-T006**: Implement `IntroBottomBar` composable (Skip + Configure buttons)
  - File: `feature/intro/src/androidMain/kotlin/org/meshtastic/feature/intro/IntroBottomBar.kt`
  - Acceptance: Bottom bar renders with configurable button text; skip button hidden when `showSkipButton=false`

- [x] **OB-T007**: Implement `PermissionScreenLayout` reusable scaffold
  - File: `feature/intro/src/androidMain/kotlin/org/meshtastic/feature/intro/PermissionScreenLayout.kt`
  - Acceptance: Layout renders headline, annotated description with tap detection, feature rows, and bottom bar

- [x] **OB-T008**: Implement `WelcomeScreen` with 3 feature highlights and "Get Started" CTA
  - File: `feature/intro/src/androidMain/kotlin/org/meshtastic/feature/intro/WelcomeScreen.kt`
  - Acceptance: Renders "Welcome to" + "Meshtastic" headings, 3 feature rows, analytics intro, no skip button

- [x] **OB-T009**: Implement `BluetoothScreen` with API-level-aware permission configuration
  - File: `feature/intro/src/androidMain/kotlin/org/meshtastic/feature/intro/BluetoothScreen.kt`
  - Acceptance: Shows 2 feature rows (discovery, config); adapts button text based on grant state; Settings link works

- [x] **OB-T010**: Implement `LocationScreen` with 4 feature highlights
  - File: `feature/intro/src/androidMain/kotlin/org/meshtastic/feature/intro/LocationScreen.kt`
  - Acceptance: Shows 4 feature rows (share, distance, filters, map); adapts button text based on grant state

- [x] **OB-T011**: Implement `NotificationsScreen` with 3 notification type highlights
  - File: `feature/intro/src/androidMain/kotlin/org/meshtastic/feature/intro/NotificationsScreen.kt`
  - Acceptance: Shows 3 feature rows (messages, nodes, battery); handles Android 13+ POST_NOTIFICATIONS

- [x] **OB-T012**: Implement `CriticalAlertsScreen` with DND override explanation
  - File: `feature/intro/src/androidMain/kotlin/org/meshtastic/feature/intro/CriticalAlertsScreen.kt`
  - Acceptance: Renders headline + description; "Configure" opens notification channel settings; "Skip" invokes onDone

- [x] **OB-T013**: Implement `introNavGraph` entry provider wiring all 5 screens with permission state
  - File: `feature/intro/src/androidMain/kotlin/org/meshtastic/feature/intro/IntroNavGraph.kt`
  - Acceptance: Full navigation flow works with permission granting, skipping, and back navigation

- [x] **OB-T014**: Implement `AppIntroductionScreen` root composable hoisting permission states
  - File: `feature/intro/src/androidMain/kotlin/org/meshtastic/feature/intro/AppIntroductionScreen.kt`
  - Acceptance: Hoists Bluetooth, Location, and Notification permission states; wires `MeshtasticNavDisplay`

---

## Phase 3 — Testing

- [x] **OB-T015**: Write `IntroViewModelTest` covering all navigation transitions
  - File: `feature/intro/src/commonTest/kotlin/org/meshtastic/feature/intro/IntroViewModelTest.kt`
  - Tests: 6 tests — Welcome→BT, BT→Location, Location→Notifications, Notifications→CriticalAlerts (granted), Notifications→null (not granted), CriticalAlerts→null
  - Acceptance: All 6 tests pass via `./gradlew :feature:intro:allTests`

---

## Gaps — Uncompleted Tasks

- [x] **OB-T100**: Extract hardcoded notification channel ID `"my_alerts"` to a shared constant or resource
  - File: `feature/intro/src/androidMain/kotlin/org/meshtastic/feature/intro/IntroNavGraph.kt` (line 112)
  - Rationale: Hardcoded string is fragile; should reference the same constant used when the channel is created.

- [x] **OB-T101**: Migrate UI screens from `androidMain` to `commonMain` using CMP-compatible permission abstraction
  - Created `IntroPermissions` and `IntroSettingsNavigator` abstractions in `commonMain`
  - Moved all 8 UI files (screens, nav graph, helpers) to `commonMain`
  - Added `AndroidIntroPermissions`/`AndroidIntroSettingsNavigator` adapters in `androidMain` (wrapping Accompanist)
  - Added JVM stubs (`JvmIntroDefaults.kt`) with always-granted permissions
  - `AppIntroductionScreen` remains in `androidMain` as thin CompositionLocal provider host
  - Added CMP `@PreviewLightDark` previews for all 5 screens

- [ ] **[DEFERRED]** **OB-T102**: Add Compose UI tests (screenshot or interaction tests) for all 5 screens — *Deferred: requires Compose UI test infrastructure.*
  - Rationale: Only ViewModel logic is unit-tested. No UI rendering or interaction tests exist. Consider `@Preview` screenshot tests or Compose test rule tests.

- [ ] **[DEFERRED]** **OB-T103**: Add accessibility verification — ensure all icons have content descriptions, touch targets ≥ 48dp, and TalkBack announces screen transitions — *Deferred: requires accessibility testing infrastructure.*
  - Rationale: Design Standards Compliance (Constitution §V) requires accessibility review. `FeatureRow` icons use `contentDescription` but no formal audit has been done.

