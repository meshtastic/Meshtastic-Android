# `:desktop` — Meshtastic Desktop

A Compose Desktop application target — the first full non-Android target for the shared KMP module graph. This module serves as:

1. **First multi-target milestone** — Proves the KMP architecture supports real application targets beyond Android.
2. **Build smoke-test** — Validates that all `core:*` KMP modules compile and link on a JVM Desktop target.
3. **Shared navigation proof** — Uses the same Navigation 3 routes from `core:navigation` and the same `NavDisplay` + `entryProvider` pattern as the Android app, proving the shared backstack architecture works cross-target.
4. **Desktop app scaffold** — A working Compose Desktop application with a `NavigationRail` for top-level destinations and placeholder screens for each feature.

## Quick Start

```bash
# Run the desktop app
./gradlew :desktop:run

# Run tests
./gradlew :desktop:test

# Package native distribution (DMG/MSI/DEB)
./gradlew :desktop:packageDistributionForCurrentOS
```

## Architecture

The module depends on the JVM variants of KMP modules:

- `core:common`, `core:model`, `core:di`, `core:navigation`, `core:repository`
- `core:domain`, `core:data`, `core:database`, `core:datastore`, `core:prefs`
- `core:network`, `core:resources`, `core:ui`

**Navigation:** Uses JetBrains multiplatform forks of Navigation 3 (`org.jetbrains.androidx.navigation3:navigation3-ui`) and Lifecycle (`org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose`, `lifecycle-runtime-compose`). A unified `SavedStateConfiguration` with polymorphic `SerializersModule` is provided centrally by `core:navigation` for non-Android NavKey serialization. Desktop utilizes the exact same navigation graph wiring (`settingsGraph`, `nodesGraph`, `contactsGraph`, `connectionsGraph`) directly from the `commonMain` of their respective feature modules, maintaining full UI parity.

**Coroutines:** Requires `kotlinx-coroutines-swing` for `Dispatchers.Main` on JVM/Desktop. Without it, any code using `lifecycle.coroutineScope` or `Dispatchers.Main` (e.g., `NodeRepositoryImpl`, `RadioConfigRepositoryImpl`) will crash at runtime.

**DI:** A Koin DI graph is bootstrapped in `Main.kt` with platform-specific implementations injected.

**UI:** JetBrains Compose for Desktop with Material 3 theming. Desktop acts as a thin host shell, delegating almost entirely to fully shared KMP UI modules.

**Localization:** Desktop exposes a language picker, persisting the selected BCP-47 tag in `UiPreferencesDataSource.locale`. `Main.kt` applies the override to the JVM default `Locale` and uses a `staticCompositionLocalOf`-backed recomposition trigger so Compose Multiplatform `stringResource()` calls update immediately without recreating the Navigation 3 backstack.

## Key Files

| File | Purpose |
|---|---|
| `Main.kt` | App entry point — Koin bootstrap, Compose Desktop window, theme + locale application |
| `DemoScenario.kt` | Offline demo data for testing without a connected device |
| `ui/DesktopMainScreen.kt` | Navigation 3 shell — `NavigationRail` + `NavDisplay` |
| `navigation/DesktopNavigation.kt` | Nav graph entry registrations for all top-level destinations (delegates to shared feature graphs) |
| `radio/DesktopRadioTransportFactory.kt` | Provides TCP, Serial/USB, and BLE transports |
| `radio/DesktopMeshServiceController.kt` | Mesh service lifecycle — orchestrates `want_config` handshake chain |
| `radio/DesktopMessageQueue.kt` | Message queue for outbound mesh packets |
| `di/DesktopKoinModule.kt` | Koin module with stub implementations |
| `di/DesktopPlatformModule.kt` | Platform-specific Koin bindings |
| `stub/NoopStubs.kt` | No-op implementations for all repository interfaces |

## What This Validates

| Module | What's Tested |
|---|---|
| `core:common` | `Base64Factory`, `NumberFormatter`, `UrlUtils`, `DateFormatter`, `CommonUri` |
| `core:model` | `DeviceVersion`, `Capabilities`, `SfppHasher`, `platformRandomBytes`, `getShortDateTime`, `Channel.getRandomKey` |
| `core:ui` | Shared Compose components compile and render on Desktop |
| Build graph | All core modules compile and link without Android SDK |

## Roadmap

- [x] Implement real navigation with shared `core:navigation` routes (Navigation 3 shell)
- [x] Adopt JetBrains multiplatform forks for lifecycle and navigation3
- [x] Wire `feature:settings` composables into the nav graph (first real feature — ~30 screens)
- [x] Wire `feature:node` composables into the nav graph (node list with shared ViewModel + NodeItem)
- [x] Wire `feature:messaging` composables into the nav graph (contacts list with shared ViewModel)
- [x] Add JetBrains Material 3 Adaptive `ListDetailPaneScaffold` to node and messaging screens
- [x] Implement TCP transport (`DesktopRadioTransportFactory`) with auto-reconnect and backoff retry
- [x] Implement mesh service controller (`DesktopMeshServiceController`) with full `want_config` handshake
- [x] Create connections screen using shared `feature:connections` with dynamic transport detection
- [x] Replace 5 placeholder config screens with real desktop implementations (Device, Position, Network, Security, ExtNotification)
- [x] Add desktop language picker backed by shared `UiPreferencesDataSource.locale` with live translation updates
- [ ] Wire remaining `feature:*` composables (map) into the nav graph
- [ ] Move remaining node detail and message composables from `androidMain` to `commonMain`
- [x] Add serial/USB transport for direct radio connection on Desktop
- [x] Add BLE transport (via Kable) for direct radio connection on Desktop
- [ ] Add MQTT transport for cloud-connected operation
- [x] Package as native distributions (DMG, MSI, DEB) via CI release pipeline
