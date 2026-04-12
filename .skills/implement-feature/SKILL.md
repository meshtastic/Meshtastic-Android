# Skill: Implement a Feature

## Description
A step-by-step workflow for implementing a new feature in the Meshtastic-Android codebase, ensuring KMP compatibility and proper architecture.

## Workflow

### 1. Update Dependencies & Aliases
- Check `gradle/libs.versions.toml` before adding libraries.
- Use `jetbrains-*` aliases for lifecycle/navigation/adaptive dependencies in `commonMain`.
- Use `compose-multiplatform-*` aliases for CMP dependencies.

### 2. Define the State & ViewModels
- Follow MVI/UDF patterns.
- Extend shared ViewModel logic in `feature/<name>/src/commonMain/kotlin/org/meshtastic/feature/<name>/<Name>ViewModel.kt`.
- Use `stateInWhileSubscribed` (from `core:ui`) for sharing state flows.
- Keep the ViewModel free of Android framework dependencies.

### 3. Build the UI
- Use Jetpack Compose Multiplatform (CMP).
- Define strings in `core:resources` (see the `compose-ui` skill).
- Support adaptive layouts (Large/XL breakpoints).

### 4. Wire Navigation & DI
- Define typed route objects in `core:navigation`.
- Export the navigation graph as an extension function on `EntryProviderScope<NavKey>` in `commonMain` (e.g., `fun EntryProviderScope<NavKey>.myFeatureGraph()`).
- Add the required DI bindings via Koin Annotations (`@Factory`, `@Single`, `@KoinViewModel`) in `commonMain`.
- **CRITICAL:** Ensure the module is registered in the app root graphs (`AppKoinModule.kt`, `DesktopKoinModule.kt`) and the navigation is injected into the root entry provider in the host shell.

### 5. Validate Platform Separation
- If you need a platform-specific API (like camera or specific mapping SDK), define an interface in `commonMain`, implement it in the host shell, and inject it via `CompositionLocal` or Koin.

### 6. Verify Locally
- Run the baseline checks (see `testing-ci` skill):
  ```bash
  ./gradlew spotlessCheck detekt assembleDebug test allTests
  ```
