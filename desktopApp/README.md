# `:desktopApp` — Meshtastic Desktop

The Compose Desktop host for the shared KMP module graph. It runs the same `core:*` modules and the same `feature:*` screens and Navigation 3 graphs as the Android app, and adds only what a JVM desktop needs: the window and tray, TCP, serial/USB and BLE transports, native notifications, and packaging.

## Quick Start

```shell
# Run the desktop app
./gradlew :desktopApp:run

# Run it with live recomposition on source edits (needs the JetBrains Runtime the toolchain pins)
./gradlew :desktopApp:hotRun

# Run tests
./gradlew :desktopApp:test

# Package native distribution for this OS, debug (no ProGuard)
./gradlew :desktopApp:packageDistributionForCurrentOS

# Package native distribution for this OS, release (ProGuard)
./gradlew :desktopApp:packageReleaseDistributionForCurrentOS
```

Packaging builds the formats of the host it runs on: DMG on macOS, MSI and EXE on Windows, DEB, RPM and a jpackage app-image on Linux. CI wraps that app-image into a real `.AppImage` with `scripts/build-appimage.sh`. A release build (`-Pdesktop.release=true`) sets `IS_DEBUG=false`, which turns off debug HTTP logging and turns on the update check.

## Distribution

Releases ship the installers on [GitHub Releases](https://github.com/meshtastic/Meshtastic-Android/releases) and a Flatpak on [Flathub](https://flathub.org/apps/org.meshtastic.MeshtasticDesktop). `RELEASE_PROCESS.md` covers signing and the store workflows.

A Homebrew cask is rendered from `.github/homebrew/meshtastic-desktop.rb` by the `update-homebrew-cask` job in `promote.yml`, which opens a PR against [meshtastic/homebrew-tap](https://github.com/meshtastic/homebrew-tap) on each production release when `HOMEBREW_TAP_TOKEN` is set. `brew install --cask meshtastic/tap/meshtastic-desktop` works once one of those PRs has merged. The cask is Apple silicon only.

## ProGuard / Minification

Release builds run ProGuard 7.9.1 to shrink the app. Obfuscation is off (`obfuscate.set(false)`), and `-dontoptimize` in `proguard-rules.pro` turns the optimizer off as well, overriding `optimize.set(true)` in `build.gradle.kts`, because the Compose Multiplatform consumer rules carry `-assumenosideeffects` directives the optimizer would act on.

**Configuration:**
- `config/proguard/shared-rules.pro`: keep rules shared with the Android R8 build, for Compose Multiplatform resources, Koin Annotations, kotlinx coroutines, serialization and datetime, Ktor, DataStore, Wire, the bundled SQLite driver and Room KMP (`androidx.room3`). Compose Desktop's ProGuard task does not read the `META-INF/proguard/*.pro` consumer rules inside dependency jars, so they are inlined here.
- `proguard-rules.pro`: desktop-only rules, for JNA (libnotify and Kable's btleplug bindings), dbus-java for the Linux tray, the MapLibre and Filament FFI bindings, LWJGL, jSerialComm and Vico.

**Troubleshooting ProGuard issues:**
- If the release build crashes at runtime with `ClassNotFoundException` or `NoSuchMethodError`, a library is loading classes via reflection that ProGuard stripped. Add a `-keep` rule to `config/proguard/shared-rules.pro` when the library is on both targets, or to `proguard-rules.pro` when it is desktop-only.
- To debug which classes ProGuard removes, temporarily add `-printusage <file>` to the rules file; `-printseeds <file>` lists what the keep rules matched.
- Run `./gradlew :desktopApp:runRelease` for a quick smoke-test of the minified app before packaging.

## Architecture

The module depends on the JVM variants of the KMP modules: every `core:*` module except `core:nfc`, `core:barcode`, `core:konsist` and `core:testing`, and every `feature:*` module except `feature:widget` (`feature:map-terrain` arrives through `feature:map-maplibre`). The map is `feature:map-maplibre`, shared with the `fdroid` Android flavor, with exactly one MapLibre native runtime chosen for the build host's architecture.

**Navigation:** Uses the JetBrains multiplatform forks of Navigation 3 (`org.jetbrains.androidx.navigation3:navigation3-ui`) and Lifecycle. `DesktopMainScreen` assembles the shared `MeshtasticAppShell`, `MeshtasticNavigationSuite` and `MeshtasticNavDisplay`, and `desktopNavGraph` registers the graphs each feature module exports from `commonMain` (`nodesGraph`, `contactsGraph`, `channelsGraph`, `mapGraph`, `settingsGraph`, `connectionsGraph`, `discoveryGraph`, `firmwareGraph`, `wifiProvisionGraph`).

**Coroutines:** Requires `kotlinx-coroutines-swing` for `Dispatchers.Main` on JVM/Desktop. Without it, any code using `lifecycle.coroutineScope` or `Dispatchers.Main` crashes at runtime.

**DI:** `Main.kt` starts Koin with `startKoin<DesktopKoinApp>`. `DesktopKoinModule` includes the `commonMain` modules of the core libraries, then the desktop modules, so a desktop binding wins over any `commonMain` default it replaces.

**UI:** JetBrains Compose for Desktop with Material 3 theming. Desktop is a thin host shell that delegates almost entirely to the shared KMP UI modules. The tray uses Compose's `Tray` on macOS and Windows, and a StatusNotifierItem over D-Bus on Linux (`tray/SniTray.kt`), since AWT's XEmbed tray loses clicks on GNOME and Plasma.

**Notifications:** `DesktopNotificationManager` implements the common `NotificationManager` interface and forwards to a native sender: `UNUserNotificationCenter` on macOS, libnotify on Linux, a PowerShell toast on Windows. It falls back to Compose `TrayState` notifications when native delivery fails. macOS requires a consistent `bundleID` (configured in `build.gradle.kts`) for proper app attribution.

**Localization:** Desktop exposes a language picker that persists the selected BCP-47 tag in `UiPrefs.locale`. `Main.kt` resolves it against the system locale (`resolveLocale`) and applies it as the JVM default `Locale`.

**Updates:** Release builds outside Flatpak check GitHub's `releases/latest` at startup (`UpdateChecker`) and post a notification when a newer production release exists. Flatpak installs update through Flathub.

## Key Files

| File | Purpose |
|---|---|
| `Main.kt` | App entry point: Koin bootstrap, Compose Desktop window and tray, theme and locale application, update check |
| `ui/DesktopMainScreen.kt` | Navigation 3 shell: shared app shell, navigation suite and `MeshtasticNavDisplay` |
| `navigation/DesktopNavigation.kt` | Nav graph entry registrations for all top-level destinations (delegates to shared feature graphs) |
| `radio/DesktopRadioTransportFactory.kt` | Provides TCP, Serial/USB (jSerialComm) and BLE (Kable) transports |
| `radio/DesktopMessageQueue.kt` | Sends queued outbound messages straight through the active radio controller (no WorkManager) |
| `notification/DesktopMeshNotificationManager.kt` | Desktop `MeshNotificationManager`; converts mesh events into `Notification`s and dispatches them |
| `notification/NativeNotificationSender.kt` | Native-sender contract, implemented by `MacOSNotificationSender` / `WindowsNotificationSender` / `LinuxNotificationSender` |
| `DesktopNotificationManager.kt` | Bridge between repository notifications, the native senders and the Compose `TrayState` fallback |
| `UpdateChecker.kt` | Compares the running version with GitHub's latest production release |
| `tray/` | Linux StatusNotifierItem tray and its D-Bus menu |
| `map/DesktopMapViewProvider.kt` | MapLibre map surfaces, imported map layers and the Site Planner slot |
| `di/DesktopKoinModule.kt` | Aggregate Koin module that `DesktopKoinApp` points at |
| `di/DesktopRuntimeModule.kt` | Radio stack, notifications and networking the JVM host owns |
| `di/DesktopStubsModule.kt` | Bindings for interfaces whose only real implementation needs Android APIs |
| `stub/NoopStubs.kt`, `stub/CompassStubs.kt` | The no-op implementations those bindings use |

## Tests

`src/test/` covers the desktop host itself: Koin graph resolution (`DesktopKoinTest`), top-level destination parity with Android (`DesktopTopLevelDestinationParityTest`), the native notification senders and the notification managers, the message queue, the Linux tray, locale resolution, the update checker, the HTTP cache and logging. The shared modules' own tests run under `allTests`.
