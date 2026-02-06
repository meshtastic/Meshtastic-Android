# CLAUDE.md - Meshtastic Android

This file provides context for Claude Code (and other AI assistants) working on the Meshtastic-Android codebase. For human contributor guidelines, see `CONTRIBUTING.md`. For a broader agent guide, see `AGENTS.md`.

## Project Summary

Meshtastic-Android is a native Kotlin Android client for Meshtastic mesh radios. It communicates with LoRa devices over Bluetooth, USB, and TCP to enable off-grid, decentralized mesh networking. The app follows Modern Android Development (MAD) principles with Jetpack Compose, Hilt DI, Room, and Protobuf.

## Quick Reference Commands

```bash
# Format code (MUST run before committing)
./gradlew spotlessApply

# Static analysis
./gradlew detekt

# Build debug APKs (both flavors)
./gradlew assembleDebug

# Unit tests
./gradlew test

# Specific flavor unit tests
./gradlew testGoogleDebug
./gradlew testFdroidDebug

# Instrumented tests (requires emulator/device)
./gradlew connectedAndroidTest

# Lint checks
./gradlew lintFdroidDebug lintGoogleDebug

# Full CI validation sequence
./gradlew spotlessCheck detekt assembleDebug test
```

## Architecture Overview

```
Presentation (Jetpack Compose + Material 3)
    |
    v
ViewModels (@HiltViewModel, StateFlow<UiState>)
    |
    v
Data Layer (Repositories in :core:data)
    |
    v
Data Sources: Room DB (:core:database) | DataStore (:core:datastore) | Device Protocol (:core:proto)
    |
    v
Device Communication (:core:service) via Bluetooth / USB / TCP
```

**Key patterns:** MVVM, Unidirectional Data Flow (UDF), Repository pattern, Constructor injection via Hilt.

## Module Structure

| Module | Purpose |
|--------|---------|
| `app/` | Main application entry point. `MainActivity`, `AppNavigation`, Hilt setup. Package: `com.geeksville.mesh` |
| `core/analytics` | DataDog analytics (Google flavor only) |
| `core/api` | AIDL interface (`IMeshService.aidl`) for third-party app integration |
| `core/barcode` | QR code generation and scanning |
| `core/common` | Shared utilities and extension functions |
| `core/data` | Repository implementations bridging UI and data sources |
| `core/database` | Room database (entities, DAOs, migrations) |
| `core/datastore` | Proto DataStore for preferences |
| `core/di` | Hilt modules and DI configuration |
| `core/model` | Parcelable data classes (`DataPacket`, `MeshUser`, `NodeInfo`, `Position`) |
| `core/navigation` | Type-safe navigation route definitions (Kotlin Serialization) |
| `core/network` | HTTP networking (Ktor) |
| `core/nfc` | NFC tag support |
| `core/prefs` | Legacy preferences |
| `core/proto` | Protobuf definitions (Wire) for device communication (Git submodule) |
| `core/service` | Mesh service implementation, BLE/USB/TCP transports |
| **`core/strings`** | **All UI strings go here.** Compose Multiplatform Resources. |
| `core/ui` | Shared Compose UI components (Material 3) |
| `feature/firmware` | Firmware update (OTA, DFU, UF2) |
| `feature/intro` | Onboarding screens |
| `feature/map` | Map visualization (Google Maps in `google` flavor, OSMDroid in `fdroid`) |
| `feature/messaging` | Chat and direct messaging |
| `feature/node` | Node information and management |
| `feature/settings` | Application settings |
| `build-logic/` | Gradle convention plugins (`meshtastic.android.library`, `meshtastic.hilt`, etc.) |

## Critical Rules

### Strings

**Never** add UI strings to `app/src/main/res/values/strings.xml`. Use the Compose Multiplatform Resource system:

1. Define strings in `core/strings/src/commonMain/composeResources/values/strings.xml`
2. Use in Composables:
```kotlin
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.strings.Res
import org.meshtastic.core.strings.your_string_key

Text(text = stringResource(Res.string.your_string_key))
```

### Dependencies

**Never** hardcode dependency versions in `build.gradle.kts` files. All versions go in `gradle/libs.versions.toml`. Apply plugins using catalog aliases.

### Build Flavors

- **`google`** - Google Play: includes Play Services, Maps, Firebase, DataDog
- **`fdroid`** - F-Droid/FOSS: no proprietary Google services, no analytics

Never add proprietary/non-FOSS dependencies to the `fdroid` flavor.

### Package Naming

- Legacy code in `app/`: `com.geeksville.mesh.*`
- Core modules: `org.meshtastic.core.*`
- Feature modules: `org.meshtastic.feature.*`

Respect the existing package of the file you are editing. New modules use `org.meshtastic.*`.

### Versioning

Do not manually edit `versionCode` or `versionName`. These are managed by the build system and CI/CD via `config.properties`.

## Code Conventions

### ViewModels
```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(
    private val repository: MyRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
}
```

### Composables
- Use `@Preview` functions for all new Composables
- Collect state with `collectAsStateWithLifecycle()`
- Use Material 3 / Material 3 Expressive components
- PascalCase naming for `@Composable` functions

### Navigation
- Routes are `@Serializable` data classes/objects in `core/navigation`
- Main `NavHost` is in `app/src/main/java/com/geeksville/mesh/ui/Main.kt`
- Use type-safe navigation with Kotlin Serialization

### Coroutines
- Use `viewModelScope` for ViewModel coroutines (never `GlobalScope`)
- Expose data as `StateFlow` or `Flow`
- Use `Dispatchers.IO` for service-level I/O scopes

### Error Handling
- Sealed classes for typed errors (e.g., `BleError`)
- `exceptionReporter()` for exception logging
- Kermit (`co.touchlab.kermit.Logger`) for logging

### Testing
- Unit tests: `src/test/` with JUnit 4, MockK, Robolectric
- Instrumented tests: `src/androidTest/` with Compose testing APIs
- Write tests for new logic; update tests for changed behavior

## Build Configuration

| Property | Value |
|----------|-------|
| Kotlin | 2.3.10 |
| Gradle | 9.3.1 |
| Compile SDK | 36 (Android 15) |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 36 |
| JDK | 17 |
| Compose BOM | 2026.01.01 |
| Hilt | 2.59.1 |
| Room | 2.8.4 |
| Wire (Protobuf) | 6.0.0-alpha02 |

## CI Pipeline

PRs must pass: `spotlessCheck`, `detekt`, `lint{Flavor}Debug`, `assembleDebug`, `test{Flavor}Debug`. Instrumented tests run on API 35 emulators. Coverage reported via Kover + Codecov.

## Agent Workflow

1. **Read first** - Understand the relevant modules and `build.gradle.kts` before making changes
2. **Plan** - Identify which `core/` or `feature/` modules need modification
3. **Implement** - Follow existing patterns in the module you're editing
4. **Format** - Run `./gradlew spotlessApply`
5. **Analyze** - Run `./gradlew detekt`
6. **Test** - Run relevant unit tests (e.g., `./gradlew :feature:settings:testGoogleDebug`)
7. **Verify build** - Run `./gradlew assembleDebug` if changes are broad

## Troubleshooting

- **`Res.string.xyz` unresolved**: Import `org.meshtastic.core.strings.Res` and run a build to generate resources
- **Build failures**: Check `gradle/libs.versions.toml` for version conflicts; try `./gradlew clean`
- **Configuration cache issues**: Add `--no-configuration-cache` flag
- **Missing secrets**: Copy `secrets.defaults.properties` to `local.properties`
