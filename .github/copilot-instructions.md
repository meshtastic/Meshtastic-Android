# Copilot Instructions for Meshtastic-Android

## Repository Summary

Meshtastic-Android is a native Android client application for the Meshtastic mesh networking project. It enables users to communicate via off-grid, decentralized mesh networks using LoRa radios. The app is written in Kotlin and follows modern Android development practices.

**Key Repository Details:**
- **Language:** Kotlin (primary), with some Java and AIDL files
- **Build System:** Gradle with Kotlin DSL
- **Size:** ~3MB source code across 3 modules 
- **Target Platform:** Android API 26+ (Android 8.0+), targeting API 36
- **Architecture:** Modern Android with Jetpack Compose, Hilt DI, Room database
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
```

### Time Requirements
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
├── network/                      # HTTP API networking library
├── mesh_service_example/         # AIDL service usage example
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
- **Dependency Injection:** Hilt
- **Navigation:** Jetpack Navigation Compose
- **Local Data:** Room database + DataStore preferences
- **Remote Data:** Custom Bluetooth/WiFi protocol + HTTP API (network module)
- **Background Work:** WorkManager
- **Communication:** AIDL service interface (`IMeshService.aidl`)

## Continuous Integration

### GitHub Workflows (.github/workflows/)
- **pull-request.yml** - Runs on every PR: build, detekt, tests
- **reusable-android-build.yml** - Shared build logic: spotless, detekt, lint, assemble, test
- **reusable-android-test.yml** - Instrumented tests on Android emulators (API 26, 35)

### CI Commands (Must Pass)
```bash
# Exact commands run in CI that must pass:
./gradlew :app:spotlessCheck :app:detekt :app:lintFdroidDebug :app:lintGoogleDebug :app:assembleDebug :app:testFdroidDebug :app:testGoogleDebug --configuration-cache --scan
./gradlew :app:connectedFdroidDebugAndroidTest :app:connectedGoogleDebugAndroidTest --configuration-cache --scan
```

### Validation Steps
1. **Code Style:** Spotless check (auto-fixable with `spotlessApply`)
2. **Static Analysis:** Detekt with custom rules in `config/detekt/detekt.yml`
3. **Lint Checks:** Android lint for both flavors
4. **Unit Tests:** JUnit tests in `app/src/test/`
5. **UI Tests:** Compose UI tests in `app/src/androidTest/`

## Common Issues & Solutions

### Build Failures
- **Gradle version error:** Ensure JDK 17 (Compatible version)
- **Missing secrets:** Copy `secrets.defaults.properties` → `local.properties`
- **Configuration cache:** Add `--no-configuration-cache` flag if issues persist
- **Clean state:** Always run `./gradlew clean` before debugging build issues

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
- **Main Activity:** `app/src/main/java/com/geeksville/mesh/MainActivity.kt`
- **Service Interface:** `core/api/src/main/aidl/org/meshtastic/core/service/IMeshService.aidl`
- **UI Screens:** `feature/*/src/main/kotlin/org/meshtastic/feature/*/`
- **Data Layer:** `core/data/src/main/kotlin/org/meshtastic/core/data/`
- **Database:** `core/database/src/main/kotlin/org/meshtastic/core/database/`
- **Models:** `core/model/src/main/kotlin/org/meshtastic/core/model/`

### Dependencies
- **Non-obvious deps:** Protobuf for device communication, DataDog for analytics (Google flavor)
- **Flavor-specific:** Google Services (google flavor), no analytics (fdroid flavor)
- **Version catalog:** Dependencies defined in `gradle/libs.versions.toml`

## Agent Instructions

**TRUST THESE INSTRUCTIONS** - they are validated and comprehensive. Only search for additional information if:
1. Commands fail with unexpected errors
2. Information appears outdated 
3. Working on areas not covered above

**Always prefer:** Using the documented commands over exploring alternatives, as they are tested and proven to work in the CI environment.

**For code changes:** Follow the architecture patterns established in existing code, maintain the modular structure, and ensure all validation steps pass before submitting changes.