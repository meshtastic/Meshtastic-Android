# DI and Navigation 3 Anti-Patterns Playbook

This playbook is a fast guardrail for high-risk mistakes in dependency injection and navigation.

Version note: align guidance with repository-pinned versions in `gradle/libs.versions.toml` (currently Koin `4.2.x` and Navigation 3 JetBrains fork `1.1.x`).

## DI anti-patterns

- Don't put Android framework dependencies (`Context`, `Activity`, `Application`) into shared `commonMain` business logic.
- Do use `@Module`, `@ComponentScan`, and `@KoinViewModel` annotations directly in `commonMain` shared modules. This provides compile-time safety and encapsulates dependency graphs per feature, which is the recommended 2026 KMP practice for Koin 4.x.
- Don't instantiate ViewModels or service dependencies manually in Compose or activities.
- Do resolve app-layer wrappers via Koin (`koinViewModel()` / injected bindings).
- Don't spread DI graph setup across unrelated modules without registration in app startup.
- Do ensure modules are reachable from app bootstrap in `app/src/main/kotlin/org/meshtastic/app/MeshUtilApplication.kt`.
- Don't assume feature/core `@Module` classes are active automatically.
- Do ensure they are included by the app root module (`@Module(includes = [...])`) in `app/src/main/kotlin/org/meshtastic/app/di/AppKoinModule.kt`.
- **Don't use Koin K2 Compiler Plugin's A1 Module Compile Safety checks for inverted dependencies.**
- **Do** leave A1 `compileSafety` disabled in `build-logic/convention/src/main/kotlin/KoinConventionPlugin.kt` (uses typed `KoinGradleExtension`). We rely on Koin's A3 full-graph validation (`startKoin` / `VerifyModule`) to handle our decoupled Clean Architecture design where interfaces are declared in one module and implemented in another.
- **Don't** expect Koin to inject default parameters automatically. The K2 plugin's `skipDefaultValues = true` (default behavior) will cause Koin to skip parameters that have default Kotlin values.

### Current code anchors (DI)

- App-level module scanning: `app/src/main/kotlin/org/meshtastic/app/MainKoinModule.kt`
- App startup + Koin init: `app/src/main/kotlin/org/meshtastic/app/MeshUtilApplication.kt`
- Shared ViewModel base: `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/MessageViewModel.kt`
- Shared base UI ViewModel: `core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/viewmodel/BaseUIViewModel.kt`

## Navigation 3 anti-patterns

- Don't reintroduce controller-coupled navigation APIs for shared flow state.
- Do use Navigation 3 types (`NavKey`, `NavBackStack`, `EntryProviderScope`) consistently.
- Don't build route identifiers as ad-hoc strings in feature code when typed route keys already exist.
- Do keep route definitions in `core:navigation` and use typed route objects.
- Don't mutate back navigation with custom stacks disconnected from app backstack.
- Do mutate `NavBackStack<NavKey>` with `add(...)` and `removeLastOrNull()`.
- Don't use Android's `androidx.activity.compose.BackHandler` or custom `PredictiveBackHandler` in multiplatform UI.
- Do use the official KMP `NavigationBackHandler` from `androidx.navigationevent:navigationevent-compose` for back gestures.
- Don't parse deep links manually in platform code or push single routes without a backstack.
- Do use `DeepLinkRouter.route()` in `core:navigation` to synthesize the correct typed backstack from RESTful paths.
- **Don't use a single `NavBackStack` list for multiple tabs, nor reuse the same `NavEntryDecorator` instances across different backstacks.**
- **Do** use `MultiBackstack` (from `core:navigation`) to manage independent `NavBackStack` instances per tab. When rendering the active tab in `MeshtasticNavDisplay`, you **must** supply a fresh set of decorators (using `remember(backStack) { ... }`) bound to the active backstack instance. Failure to swap decorators when swapping backstacks causes Navigation 3 to perceive the inactive entries as "popped", permanently destroying their `ViewModelStore` and saved UI state.

### Current code anchors (Navigation 3)

- Typed routes: `core/navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/Routes.kt`
- Shared saved-state config: `core/navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/NavigationConfig.kt`
- App root backstack + `MeshtasticNavDisplay`: `app/src/main/kotlin/org/meshtastic/app/ui/Main.kt`
- Shared graph entry provider pattern: `feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/navigation/SettingsNavigation.kt`
- Desktop Navigation 3 shell: `desktop/src/main/kotlin/org/meshtastic/desktop/ui/DesktopMainScreen.kt`
- Desktop nav graph assembly: `desktop/src/main/kotlin/org/meshtastic/desktop/navigation/DesktopNavigation.kt`


## Quick pre-PR checks for DI/navigation edits

- Verify affected graph/module is registered and reachable from app startup.
- Verify no new Android framework type leaks into `commonMain`.
- Verify routes/backstack use typed keys and Navigation 3 primitives.
- Run targeted verification from `docs/agent-playbooks/testing-and-ci-playbook.md`.
