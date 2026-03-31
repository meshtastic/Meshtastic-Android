# Task Playbooks

Use these as practical recipes. Keep edits minimal and aligned with existing module boundaries.

## Playbook A: Add or update a user-visible string

1. Add/update key in `core/resources/src/commonMain/composeResources/values/strings.xml`.
2. Import generated resource symbol in UI code (`org.meshtastic.core.resources.<key>`).
3. Use `stringResource(Res.string.<key>)` in Compose.
4. If the string appears in a shared dialog, prefer `core:ui` dialog components.
5. Verify no hardcoded user-facing strings were introduced.

Reference examples:
- `feature/node/src/androidMain/kotlin/org/meshtastic/feature/node/list/NodeListScreen.kt`
- `core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/component/AlertDialogs.kt`

## Playbook B: Add shared ViewModel logic in a feature module

1. Implement or extend base ViewModel logic in `feature/<name>/src/commonMain/...`.
2. Keep shared class free of Android framework dependencies.
3. Keep Android framework dependencies out of shared logic; if the module already uses Koin annotations in `commonMain`, keep patterns consistent and ensure app root inclusion.
4. Update shared navigation entry points in `feature/*/src/commonMain/kotlin/org/meshtastic/feature/*/navigation/...` to resolve ViewModels with `koinViewModel()`.

Reference examples:
- Shared base: `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/MessageViewModel.kt`
- Shared base UI ViewModel: `core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/viewmodel/BaseUIViewModel.kt`
- Navigation usage: `feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/navigation/SettingsNavigation.kt`
- Desktop navigation usage: `desktop/src/main/kotlin/org/meshtastic/desktop/navigation/DesktopSettingsNavigation.kt`

## Playbook C: Add a new dependency or service binding

1. Check `gradle/libs.versions.toml` for existing library and version alias.
2. Add new dependency to version catalog first (if truly new).
3. Wire implementation in the owning module (`core:*`, `feature:*`, or `app`) following existing architecture.
4. Register bindings/modules in app Koin graph where needed.
5. For Android system integration (WorkManager, service bootstrapping), wire via `MeshUtilApplication` and app-layer modules.

Reference examples:
- App startup and Koin bootstrap: `app/src/main/kotlin/org/meshtastic/app/MeshUtilApplication.kt`
- App module scan: `app/src/main/kotlin/org/meshtastic/app/MainKoinModule.kt`

## Playbook D: Add or modify navigation flow

1. Define/extend route keys in `core:navigation`.
2. Implement feature entry/content using Navigation 3 types (`NavKey`, `NavBackStack`, `EntryProviderScope`).
3. Add graph entries under the relevant feature module's `navigation` package (e.g., `feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/navigation`).
4. If the entry content depends on platform-specific UI (e.g. Activity context or specific desktop wrappers), use `expect`/`actual` declarations for the content composables.
5. Use backstack mutation (`add`, `removeLastOrNull`) instead of introducing controller-coupled APIs.
6. Verify deep-link behavior if route is externally reachable.

Reference examples:
- Shared graph wiring: `feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/navigation/SettingsNavigation.kt`
- Shared graph content: `feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/navigation/SettingsNavigation.kt`
- Android-specific content actual: `feature/settings/src/androidMain/kotlin/org/meshtastic/feature/settings/navigation/SettingsMainScreen.kt`
- Desktop specific content: `feature/settings/src/jvmMain/kotlin/org/meshtastic/feature/settings/navigation/SettingsMainScreen.kt`
- Feature intro graph pattern: `feature/intro/src/androidMain/kotlin/org/meshtastic/feature/intro/IntroNavGraph.kt`
- Desktop nav shell: `desktop/src/main/kotlin/org/meshtastic/desktop/ui/DesktopMainScreen.kt`
- Desktop nav graph assembly: `desktop/src/main/kotlin/org/meshtastic/desktop/navigation/DesktopNavigation.kt`


## Playbook E: Add flavor/platform-specific UI implementation

1. Keep shared contracts in `core:ui` or feature shared code.
2. Inject flavor/platform implementation via `CompositionLocal` from `app`.
3. Avoid direct dependency from shared modules to Google Maps/osmdroid/other Android SDK-only APIs.
4. Keep adapter types narrow and stable (interfaces, DTO-like params).

Reference examples:
- Contract: `core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/util/MapViewProvider.kt`
- Provider wiring: `app/src/main/kotlin/org/meshtastic/app/MainActivity.kt`
- Consumer side: `feature/map/src/androidMain/kotlin/org/meshtastic/feature/map/MapScreen.kt`

## Playbook F: Onboard a new platform target

1. Create a platform application module (e.g., `desktop/`, `ios/`).
2. Copy `desktop/src/main/kotlin/org/meshtastic/desktop/stub/NoopStubs.kt` as the starting stub set. All repository interfaces have no-op implementations there.
3. Create a `<Platform>KoinModule` that mirrors `desktop/src/main/kotlin/org/meshtastic/desktop/di/DesktopKoinModule.kt` — use stubs for unimplemented interfaces, real implementations where available.
4. Add `kotlinx-coroutines-swing` (JVM/Desktop) or the equivalent platform coroutines dispatcher module. Without it, `Dispatchers.Main` is unavailable and any code using `lifecycle.coroutineScope` will crash at runtime.
5. Progressively replace stubs with real implementations (e.g., serial transport for desktop, CoreBluetooth for iOS).
6. Add `<platform>()` target to feature modules as needed (all `core:*` modules already declare `jvm()`).
7. Ensure the new module applies the expected KMP convention plugin so root `kmpSmokeCompile` auto-discovers and validates it in CI.
8. If `commonMain` code fails to compile for the new target, it's a KMP migration debt — fix the shared code, not the target.

Reference examples:
- Desktop stubs: `desktop/src/main/kotlin/org/meshtastic/desktop/stub/NoopStubs.kt`
- Desktop DI: `desktop/src/main/kotlin/org/meshtastic/desktop/di/DesktopKoinModule.kt`
- Desktop Navigation 3 shell: `desktop/src/main/kotlin/org/meshtastic/desktop/ui/DesktopMainScreen.kt`
- Desktop nav graph entries: `desktop/src/main/kotlin/org/meshtastic/desktop/navigation/DesktopNavigation.kt`
- Desktop shared feature wiring: `feature/settings/src/commonMain/kotlin/org/meshtastic/feature/settings/navigation/SettingsNavigation.kt`
- Desktop-specific screen: `feature/settings/src/jvmMain/kotlin/org/meshtastic/feature/settings/DesktopSettingsScreen.kt`
- Roadmap: `docs/roadmap.md`


