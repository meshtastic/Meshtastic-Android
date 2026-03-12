# Copilot Instructions for Meshtastic-Android

## Repository Summary

Meshtastic-Android is a native Android client application for the Meshtastic mesh networking project. It enables users to communicate via off-grid, decentralized mesh networks using LoRa radios. The app is written in Kotlin and follows modern Android development practices.

**Key Repository Details:**
- **Language:** Kotlin (primary), with some Java and AIDL files
- **Build System:** Gradle with Kotlin DSL
- **Architecture shape:** Android app shell plus a broad `core:*` / `feature:*` KMP module graph
- **Target Platform:** Android API 26+ (Android 8.0+), targeting API 36
- **Architecture:** Android-first Kotlin Multiplatform with Jetpack Compose, Koin DI, Room KMP, DataStore, and Navigation 3 shared backstack state
- **Product Flavors:** `fdroid` (F-Droid) and `google` (Google Play Store)
- **Build Types:** `debug` and `release`

## Essential Build & Test Commands

**ALWAYS run these commands in the exact order specified to avoid build failures:**

### Prerequisites Setup
1. **JDK Requirement:** JDK 17 is required (compatible with most developer environments)
2. **Secrets Configuration:** Copy `secrets.defaults.properties` to `local.properties` and update:
   ```properties
   MAPS_API_KEY=your_google_maps_api_key_here
   datadogApplicationId=your_datadog_app_id
   datadogClientToken=your_datadog_client_token
   ```
3. **Clean Environment:** Always start with `./gradlew clean` for fresh builds

### Build Commands (Validated Working Order)
```bash
# 1. ALWAYS clean first for reliable builds
./gradlew clean

# 2. Check code formatting (run before making changes)
./gradlew spotlessCheck

# 3. Apply automatic code formatting fixes
./gradlew spotlessApply

# 4. Run static code analysis/linting
./gradlew detekt

# 5. Build debug APKs for both flavors (takes 3-5 minutes)
./gradlew assembleDebug

# 6. Build specific flavor variants
./gradlew assembleFdroidDebug    # F-Droid debug build
./gradlew assembleGoogleDebug    # Google debug build
./gradlew assembleFdroidRelease  # F-Droid release build
./gradlew assembleGoogleRelease  # Google release build

# 7. Run local unit tests (takes 2-3 minutes)
./gradlew test

# 8. Run specific flavor unit tests
./gradlew testFdroidDebug
./gradlew testGoogleDebug

# 9. Run instrumented tests (requires Android device/emulator, takes 5-10 minutes)
./gradlew connectedAndroidTest

# 10. Run lint checks for both flavors
./gradlew lintFdroidDebug lintGoogleDebug

# 11. Run the desktop module
./gradlew :desktop:run
./gradlew :desktop:test
- Clean build: 3-5 minutes
- Unit tests: 2-3 minutes  
- Instrumented tests: 5-10 minutes
- Detekt analysis: 1-2 minutes
- Spotless formatting: 30 seconds

### Environment Setup
**Required Tools:**
- Android SDK API 36 (compile target)
- JDK 17 (Preferred for consistency across project and plugins)
- Gradle 9.0+ (downloaded automatically by wrapper)

**Optional but Recommended:**
- Install pre-push Git hook: `./gradlew spotlessInstallGitPrePushHook --no-configuration-cache`

## Project Architecture & Layout

### Module Structure
```
├── app/                          # Main Android application
│   ├── src/main/                 # Main source code
│   ├── src/test/                 # Unit tests
│   ├── src/androidTest/          # Instrumented tests
│   ├── src/fdroid/              # F-Droid specific code
│   └── src/google/              # Google Play specific code
├── core/                         # Core library modules
├── desktop/                      # Compose Desktop application (first non-Android KMP target)
├── feature/                      # Feature modules (all KMP with JVM targets)
│   ├── connections/              # Device connections UI (BLE, TCP, USB scanning)
│   ├── firmware/                 # Firmware update flow
│   ├── intro/                    # Onboarding flow
│   ├── map/                      # Map UI
│   ├── messaging/                # Messaging/contacts UI
│   ├── node/                     # Node list and detail UI
│   └── settings/                 # Settings screens
├── build-logic/                  # Build configuration convention plugins
└── config/                       # Linting and formatting configs
    ├── detekt/                   # Detekt static analysis rules
    └── spotless/                 # Code formatting configuration
```

### Key Configuration Files
- `config.properties` - Version constants and build config
- `app/build.gradle.kts` - Main app build configuration
- `config/detekt/detekt.yml` - Static analysis rules
- `config/spotless/.editorconfig` - Code formatting rules
- `gradle.properties` - Gradle build settings
- `secrets.defaults.properties` - Template for secrets (copy to `local.properties`)

### Architecture Components
- **UI Framework:** Jetpack Compose with Material 3
- **State Management:** Unidirectional Data Flow with ViewModels
- **Dependency Injection:** Koin Annotations with K2 compiler plugin
- **Navigation:** AndroidX Navigation 3 (JetBrains multiplatform fork) with shared navigation keys/routes in `core:navigation`
- **Lifecycle:** JetBrains multiplatform forks for `lifecycle-viewmodel-compose` and `lifecycle-runtime-compose`
- **Local Data:** Room database + DataStore preferences
- **Remote Data:** Shared BLE/network/service layers across `core:ble`, `core:network`, and `core:service`
- **Background Work:** WorkManager
- **Communication:** AIDL service interface (`IMeshService.aidl`)
- **Desktop:** First non-Android KMP target. Nav 3 shell, full Koin DI, TCP transport with `want_config` handshake, adaptive list-detail screens for nodes/messaging, ~35 settings screens, connections UI. See `docs/kmp-status.md`.

## Continuous Integration

### GitHub Workflows (.github/workflows/)
- **pull-request.yml** - PR entry workflow
- **reusable-check.yml** - Shared Android/JVM verification: spotless, detekt, unit tests, Kover, JVM smoke compile, assemble/lint, optional instrumented tests

### CI Commands (Must Pass)
```bash
# Reusable CI workflow runs these core checks on the first matrix leg:
./gradlew spotlessCheck detekt -Pci=true
./gradlew testDebugUnitTest testFdroidDebugUnitTest testGoogleDebugUnitTest koverXmlReport app:koverXmlReportFdroidDebug app:koverXmlReportGoogleDebug -Pci=true --continue
./gradlew :core:proto:compileKotlinJvm :core:common:compileKotlinJvm :core:model:compileKotlinJvm :core:repository:compileKotlinJvm :core:di:compileKotlinJvm :core:navigation:compileKotlinJvm :core:resources:compileKotlinJvm :core:datastore:compileKotlinJvm :core:database:compileKotlinJvm :core:domain:compileKotlinJvm :core:prefs:compileKotlinJvm :core:network:compileKotlinJvm :core:data:compileKotlinJvm :core:ble:compileKotlinJvm :core:nfc:compileKotlinJvm :core:service:compileKotlinJvm :core:ui:compileKotlinJvm :feature:intro:compileKotlinJvm :feature:messaging:compileKotlinJvm :feature:connections:compileKotlinJvm :feature:map:compileKotlinJvm :feature:node:compileKotlinJvm :feature:settings:compileKotlinJvm :feature:firmware:compileKotlinJvm :desktop:test -Pci=true --continue
```

### Validation Steps
1. **Code Style:** Spotless check (auto-fixable with `spotlessApply`)
2. **Static Analysis:** Detekt with custom rules in `config/detekt/detekt.yml`
3. **Shared smoke compile:** JVM compile checks for all `core:*` and `feature:*` KMP modules plus `:desktop:test`
4. **Lint Checks:** Android lint on debug variants
5. **Unit Tests:** Android/unit/shared tests plus Kover reports
6. **UI Tests:** Compose/instrumented tests when emulator runs are enabled

## Common Issues & Solutions

### Build Failures
- **Gradle version error:** Ensure JDK 17 (Compatible version)
- **Missing secrets:** Copy `secrets.defaults.properties` → `local.properties`
- **Configuration cache:** Add `--no-configuration-cache` flag if issues persist
- **Clean state:** Always run `./gradlew clean` before debugging build issues

### Desktop Issues
- **`Dispatchers.Main` missing:** JVM/Desktop requires `kotlinx-coroutines-swing` for `Dispatchers.Main`. Without it, any code using `lifecycle.coroutineScope` or `Dispatchers.Main` will crash at runtime. The desktop module already includes this dependency.

### Testing Issues
- **Instrumented tests:** Require Android device/emulator with API 26+
- **UI tests:** Use `ComposeTestRule` for Compose UI testing
- **Coroutine tests:** Use `kotlinx.coroutines.test` library

### Code Style Issues
- **Formatting:** Run `./gradlew spotlessApply` to auto-fix
- **Detekt warnings:** Check `config/detekt/detekt.yml` for rules
- **Localization:** Use `stringResource(Res.string.key)` instead of hardcoded strings

## File Organization

### Source Code Locations
- **Main Activity:** `app/src/main/kotlin/org/meshtastic/app/MainActivity.kt`
- **Service Interface:** `core/api/src/main/aidl/org/meshtastic/core/service/IMeshService.aidl`
- **Shared feature/UI code:** `feature/*/src/commonMain/kotlin/org/meshtastic/feature/*/`
- **Data Layer:** `core/data/src/commonMain/kotlin/org/meshtastic/core/data/`
- **Database:** `core/database/src/commonMain/kotlin/org/meshtastic/core/database/`
- **Models:** `core/model/src/commonMain/kotlin/org/meshtastic/core/model/`

### Dependencies
- **Non-obvious deps:** Protobuf for device communication, DataDog for analytics (Google flavor)
- **Flavor-specific:** Google Services (google flavor), no analytics (fdroid flavor)
- **Version catalog:** Dependencies defined in `gradle/libs.versions.toml`

## Agent Instructions

- Keep documentation continuously in sync with the code. If you change architecture, module targets, CI tasks, validation commands, or agent workflow rules, update the relevant docs in the same change.
- Treat `AGENTS.md` as the primary source of truth for project architecture and process; update mirrored guidance here when that source changes.
- Architecture review and gap analysis: `docs/decisions/architecture-review-2026-03.md`.
- **Platform purity:** Never import `java.*` or `android.*` in `commonMain`. Use KMP alternatives (see AGENTS.md §3B for the full list).
- **Testing:** Write ViewModel and business logic tests in `commonTest` (not `test/` Robolectric) so every target runs them.

**TRUST THESE INSTRUCTIONS** - they are validated and comprehensive. Only search for additional information if:
1. Commands fail with unexpected errors
2. Information appears outdated 
3. Working on areas not covered above

**Always prefer:** Using the documented commands over exploring alternatives, as they are tested and proven to work in the CI environment.

**For code changes:** Follow the architecture patterns established in existing code, maintain the modular structure, and ensure all validation steps pass before submitting changes.