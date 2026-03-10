# DI and Navigation 3 Anti-Patterns Playbook

This playbook is a fast guardrail for high-risk mistakes in dependency injection and navigation.

Version note: align guidance with repository-pinned versions in `gradle/libs.versions.toml` (currently Koin `4.2.x` and Navigation 3 `1.0.x`).

## DI anti-patterns

- Don't put Android framework dependencies (`Context`, `Activity`, `Application`) into shared `commonMain` business logic.
- Do keep shared logic DI-agnostic where practical, then bind it from Android/app layer wiring.
- Don't instantiate ViewModels or service dependencies manually in Compose or activities.
- Do resolve app-layer wrappers via Koin (`koinViewModel()` / injected bindings).
- Don't spread DI graph setup across unrelated modules without registration in app startup.
- Do ensure modules are reachable from app bootstrap in `app/src/main/kotlin/org/meshtastic/app/MeshUtilApplication.kt`.
- Don't assume feature/core `@Module` classes are active automatically.
- Do ensure they are included by the app root module (`@Module(includes = [...])`) in `app/src/main/kotlin/org/meshtastic/app/di/AppKoinModule.kt`.

### Current code anchors (DI)

- App-level module scanning: `app/src/main/kotlin/org/meshtastic/app/MainKoinModule.kt`
- App startup + Koin init: `app/src/main/kotlin/org/meshtastic/app/MeshUtilApplication.kt`
- Android wrapper ViewModel pattern: `app/src/main/kotlin/org/meshtastic/app/messaging/AndroidMessageViewModel.kt`
- Shared ViewModel base: `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/MessageViewModel.kt`

## Navigation 3 anti-patterns

- Don't reintroduce controller-coupled navigation APIs for shared flow state.
- Do use Navigation 3 types (`NavKey`, `NavBackStack`, `EntryProviderScope`) consistently.
- Don't build route identifiers as ad-hoc strings in feature code when typed route keys already exist.
- Do keep route definitions in `core:navigation` and use typed route objects.
- Don't mutate back navigation with custom stacks disconnected from app backstack.
- Do mutate `NavBackStack<NavKey>` with `add(...)` and `removeLastOrNull()`.

### Current code anchors (Navigation 3)

- Typed routes: `core/navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/Routes.kt`
- App root backstack + `NavDisplay`: `app/src/main/kotlin/org/meshtastic/app/ui/Main.kt`
- Graph entry provider pattern: `app/src/main/kotlin/org/meshtastic/app/navigation/SettingsNavigation.kt`
- Feature-level Navigation 3 usage: `feature/intro/src/androidMain/kotlin/org/meshtastic/feature/intro/AppIntroductionScreen.kt`

## Quick pre-PR checks for DI/navigation edits

- Verify affected graph/module is registered and reachable from app startup.
- Verify no new Android framework type leaks into `commonMain`.
- Verify routes/backstack use typed keys and Navigation 3 primitives.
- Run targeted verification from `docs/agent-playbooks/testing-and-ci-playbook.md`.



