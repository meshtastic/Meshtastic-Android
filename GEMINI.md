# Meshtastic-Android: AI Agent Instructions (GEMINI.md)

**CRITICAL AGENT DIRECTIVE:** This file contains validated, comprehensive instructions for interacting with the Meshtastic-Android repository. You MUST adhere strictly to these rules, build commands, and architectural constraints. Only deviate or explore alternatives if the documented commands fail with unexpected errors.

## 1. Project Overview & Architecture
Meshtastic-Android is a Kotlin Multiplatform (KMP) application for off-grid, decentralized mesh networks.

- **Language:** Kotlin (primary), AIDL.
- **Build System:** Gradle (Kotlin DSL). JDK 17 is REQUIRED.
- **Target SDK:** API 36. Min SDK: API 26 (Android 8.0).
- **Flavors:**
  - `fdroid`: Open source only, no tracking/analytics.
  - `google`: Includes Google Play Services (Maps) and DataDog analytics.
- **Core Architecture:** Modern Android Development (MAD) with KMP core.
  - **KMP Modules:** `core:model`, `core:proto`, `core:common`, `core:resources`, `core:database`, `core:datastore`, `core:repository`, `core:domain`, `core:prefs`, `core:network`, `core:di`, and `core:data`.
  - **UI:** Jetpack Compose (Material 3).
  - **DI:** Koin (centralized in `app` module for KMP modules).
  - **Navigation:** Type-Safe Jetpack Navigation.
  - **Room KMP:** Always use `factory = { MeshtasticDatabaseConstructor.initialize() }` in `Room.databaseBuilder` and `inMemoryDatabaseBuilder`. DAOs and Entities reside in `commonMain`.

## 2. Environment Setup (Mandatory First Steps)
Before attempting any builds or tests, ensure the environment is configured:

1. **JDK 17 MUST be used** to prevent Gradle sync/build failures.
2. **Secrets:** You must copy `secrets.defaults.properties` to `local.properties` to satisfy build requirements, even for dummy builds:
   ```properties
   # local.properties example
   MAPS_API_KEY=dummy_key
   datadogApplicationId=dummy_id
   datadogClientToken=dummy_token
   ```

## 3. Strict Execution Commands
Always run commands in the following order to ensure reliability. Do not attempt to bypass `clean` if you are facing build issues.

**Formatting & Linting (Run BEFORE committing):**
```bash
./gradlew spotlessApply  # Always run to auto-fix formatting
./gradlew detekt         # Run static analysis
```

**Building:**
```bash
./gradlew clean          # Always start here if facing issues
./gradlew assembleDebug  # Full build (fdroid and google)
```

**Testing:**
```bash
./gradlew testAndroid        # Run Android unit tests (Robolectric)
./gradlew testCommonMain     # Run KMP common tests (if applicable)
./gradlew connectedAndroidTest # Run instrumented tests
```
*Note: If testing Compose UI on the JVM (Robolectric) with Java 17, pin your tests to `@Config(sdk = [34])` to avoid SDK 35 compatibility crashes.*

## 4. Coding Standards & Mandates

- **UI Components:** Always utilize `:core:ui` for shared Jetpack Compose components (e.g., `MeshtasticResourceDialog`, `TransportIcon`). Do not reinvent standard dialogs or preference screens.
- **Strings/Localization:** **NEVER** use hardcoded strings or the legacy `app/src/main/res/values/strings.xml`.
  - **Rule:** You MUST use the Compose Multiplatform Resource library.
  - **Location:** `core/resources/src/commonMain/composeResources/values/strings.xml`.
  - **Usage:** `stringResource(Res.string.your_key)`
- **Bluetooth/BLE:** Do not use legacy Android Bluetooth callbacks. All BLE communication MUST route through `:core:ble`, utilizing Nordic Semiconductor's Android Common Libraries and Kotlin Coroutines/Flows.
- **Dependencies:** Never assume a library is available. Check `gradle/libs.versions.toml` first. If adding a new dependency, it MUST be added to the version catalog, not directly to a `build.gradle.kts` file.
- **Namespacing:** Prefer the `org.meshtastic` namespace for all new code. The legacy `com.geeksville.mesh` ApplicationId is maintained for compatibility.

## 5. Module Map
When locating code to modify, use this map:
- **`app/`**: Main application wiring and Koin modules. Package: `org.meshtastic.app`.
- **`:core:data`**: Core business logic and managers. Package: `org.meshtastic.core.data`.
- **`:core:repository`**: Domain interfaces and common models. Package: `org.meshtastic.core.repository`.
- **`:core:ble`**: Coroutine-based Bluetooth logic.
- **`:core:api`**: AIDL service interface (`IMeshService.aidl`) for third-party integrations (like ATAK).
- **`:core:ui`**: Shared Compose UI elements and theming.
- **`:feature:*`**: Isolated feature screens (e.g., `:feature:messaging` for chat, `:feature:map` for mapping).
