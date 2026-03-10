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
4. Add/update Android wrapper in `app/src/main/kotlin/org/meshtastic/app/...` with `@KoinViewModel` when Android instantiation is needed.
5. Update navigation entry points in `app/src/main/kotlin/org/meshtastic/app/navigation/...` to resolve wrapper ViewModels with `koinViewModel()`.

Reference examples:
- Shared base: `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/MessageViewModel.kt`
- Android wrapper: `app/src/main/kotlin/org/meshtastic/app/messaging/AndroidMessageViewModel.kt`
- Navigation usage: `app/src/main/kotlin/org/meshtastic/app/navigation/SettingsNavigation.kt`

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
3. Add graph entries under `app/src/main/kotlin/org/meshtastic/app/navigation`.
4. Use backstack mutation (`add`, `removeLastOrNull`) instead of introducing controller-coupled APIs.
5. Verify deep-link behavior if route is externally reachable.

Reference examples:
- App graph wiring: `app/src/main/kotlin/org/meshtastic/app/navigation/SettingsNavigation.kt`
- Feature intro graph pattern: `feature/intro/src/androidMain/kotlin/org/meshtastic/feature/intro/IntroNavGraph.kt`

## Playbook E: Add flavor/platform-specific UI implementation

1. Keep shared contracts in `core:ui` or feature shared code.
2. Inject flavor/platform implementation via `CompositionLocal` from `app`.
3. Avoid direct dependency from shared modules to Google Maps/osmdroid/other Android SDK-only APIs.
4. Keep adapter types narrow and stable (interfaces, DTO-like params).

Reference examples:
- Contract: `core/ui/src/commonMain/kotlin/org/meshtastic/core/ui/util/MapViewProvider.kt`
- Provider wiring: `app/src/main/kotlin/org/meshtastic/app/MainActivity.kt`
- Consumer side: `feature/map/src/androidMain/kotlin/org/meshtastic/feature/map/MapScreen.kt`


