# Common Practices Playbook

This document captures discoverable patterns that are already used in the repository.

## 1) Module and layering boundaries

- Keep domain logic in KMP modules (`commonMain`) and keep Android framework wiring in `app` or `androidMain`.
- Use `core:*` for shared logic, `feature:*` for user-facing flows, and `app` for Android entrypoints and integration wiring.
- Note: Former passthrough Android ViewModel wrappers have been eliminated. ViewModels are now shared KMP components. Platform-specific dependencies (file I/O, permissions) are isolated behind injected `core:repository` interfaces.

## 2) Dependency injection conventions (Koin)

- Use Koin annotations (`@Module`, `@ComponentScan`, `@KoinViewModel`, `@KoinWorker`) and keep DI wiring discoverable from `app`.
- Example app scan module: `app/src/main/kotlin/org/meshtastic/app/MainKoinModule.kt`.
- Example app startup and module registration: `app/src/main/kotlin/org/meshtastic/app/MeshUtilApplication.kt`.
- Ensure feature/core modules are included in the app root module: `app/src/main/kotlin/org/meshtastic/app/di/AppKoinModule.kt`.
- Prefer DI-agnostic shared logic in `commonMain`; inject from Android wrappers.

## 3) Navigation conventions (Navigation 3)

- Use Navigation 3 types (`NavKey`, `NavBackStack`, entry providers) instead of legacy controller-first patterns.
- Example graph using `EntryProviderScope<NavKey>` and `backStack.add/removeLastOrNull`: `feature/settings/src/androidMain/kotlin/org/meshtastic/feature/settings/navigation/SettingsNavigation.kt`.
- Example feature flow using `rememberNavBackStack` and `NavDisplay<NavKey>`: `feature/intro/src/androidMain/kotlin/org/meshtastic/feature/intro/AppIntroductionScreen.kt`.

## 4) UI and resources

- Keep shared dialogs/components in `core:ui` where possible.
- Put localizable UI strings in Compose Multiplatform resources: `core/resources/src/commonMain/composeResources/values/strings.xml`.
- Use `stringResource(Res.string.key)` from shared resources in feature screens.
- When retrieving strings in non-composable Coroutines, Managers, or ViewModels, use `getStringSuspend()`. Never use the blocking `getString()` inside a coroutine as it will crash iOS and freeze the UI thread.
- Example usage: `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/list/NodeListScreen.kt`.

## 5) Platform abstraction in shared UI

- Use `CompositionLocal` providers in `app` to inject Android/flavor-specific UI behavior into shared modules.
- Example provider wiring in `MainActivity`: `app/src/main/kotlin/org/meshtastic/app/MainActivity.kt`.
- Example abstraction contract: `core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/util/MapViewProvider.kt`.

## 6) I/O and concurrency in shared code

- In `commonMain`, use Okio streams (`BufferedSource`/`BufferedSink`) and coroutines/Flow.
- For ViewModel state exposure, prefer `stateInWhileSubscribed(...)` in shared ViewModels and collect in UI with `collectAsStateWithLifecycle()`.
- Example shared extension: `core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/viewmodel/ViewModelExtensions.kt`.
- Example Okio usage in shared domain code:
  - `core/domain/src/commonMain/kotlin/org/meshtastic/core/domain/usecase/settings/ImportProfileUseCase.kt`
  - `core/domain/src/commonMain/kotlin/org/meshtastic/core/domain/usecase/settings/ExportDataUseCase.kt`

## 7) Namespace and compatibility

- New code should use `org.meshtastic.*`.
- Keep compatibility constraints where required (notably legacy app ID and intent signatures for external integration).


