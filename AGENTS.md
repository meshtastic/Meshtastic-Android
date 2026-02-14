# Meshtastic Android - Agent Guide

This file serves as a comprehensive guide for AI agents and developers working on the `Meshtastic-Android` codebase. Use this as your primary reference for understanding the architecture, conventions, and workflows.

## 1. Project Overview

-   **Type:** Native Android Application (Kotlin).
-   **Purpose:** Client interface for Meshtastic mesh radios.
-   **Architecture:** Modern Android Development (MAD) principles.
    -   **UI:** Jetpack Compose (Material 3).
    -   **State Management:** Unidirectional Data Flow (UDF) with ViewModels, Coroutines, and Flow.
    -   **Dependency Injection:** Hilt.
    -   **Navigation:** Type-Safe Navigation (Jetpack Navigation).
    -   **Data Layer:** Repository pattern with Room (local DB), DataStore (prefs), and Protobuf (device comms).

## 2. Codebase Map

| Directory | Description |
| :--- | :--- |
| `app/` | Main application module. Contains `MainActivity`, `AppNavigation`, and Hilt entry points. Uses package `com.geeksville.mesh`. |
| `core/` | Shared library modules. Most code here uses package `org.meshtastic.core.*`. |
| `core/strings/` | **Crucial:** Centralized string resources using Compose Multiplatform Resources. |
| `feature/` | Feature modules (e.g., `settings`, `map`, `messaging`). Each is a standalone Gradle module. Uses package `org.meshtastic.feature.*`. |
| `build-logic/` | Custom Gradle convention plugins. Defines build logic for the entire project. |
| `gradle/libs.versions.toml` | **Version Catalog.** All dependencies and versions are defined here. |
| `core/proto/` | Protobuf definitions for communicating with the mesh radio. |

## 3. Development Guidelines

### A. UI Development (Jetpack Compose)
-   **Material 3:** The app uses Material 3. Look for ways to use **Material 3 Expressive** components where appropriate.
-   **Strings:**
    -   Do **not** use `app/src/main/res/values/strings.xml` for UI strings.
    -   Use the **Compose Multiplatform Resource** library in `core/strings`.
    -   **Definition:** Add strings to `core/strings/src/commonMain/composeResources/values/strings.xml`.
    -   **Usage:**
        ```kotlin
        import org.jetbrains.compose.resources.stringResource
        import org.meshtastic.core.strings.Res
        import org.meshtastic.core.strings.your_string_key

        Text(text = stringResource(Res.string.your_string_key))
        ```
-   **Dialogs:**
    -   Use the centralized `MeshtasticDialog` for all alerts and confirmation boxes.
    -   **Specialized Overloads:** Use `MeshtasticResourceDialog` (for resource-only content) or `MeshtasticTextDialog` (for mixed resource/text content) to reduce boilerplate.
    -   **Location:** Defined in `core/ui/src/main/kotlin/org/meshtastic/core/ui/component/AlertDialogs.kt`.
-   **Previews:** Create `@Preview` functions for your Composables to ensure they render correctly.

### B. Architecture & State
-   **ViewModels:** Must be annotated with `@HiltViewModel`.
-   **Injection:** Use `@Inject constructor(...)`.
-   **Scopes:** Use `viewModelScope` for coroutines. Avoid `GlobalScope`.
-   **Data Flow:** Expose UI state as `StateFlow<UiState>` or `Flow<UiState>`.

### C. Navigation
-   The project uses **Type-Safe Navigation** (Kotlin Serialization).
-   Routes are defined in `core/navigation` (e.g., `ContactsRoutes`, `SettingsRoutes`).
-   The main `NavHost` is located in `app/src/main/java/com/geeksville/mesh/ui/Main.kt`.

### D. Dependency Management
-   **Never** hardcode versions in `build.gradle.kts` files.
-   **Action:** Add the library and version to `gradle/libs.versions.toml`.
-   **Action:** Apply plugins using the alias from the catalog (e.g., `alias(libs.plugins.meshtastic.android.library)`).
-   **Alpha Libraries:** Do not be shy about using alpha libraries from Google if they provide necessary features.

### E. Build Variants (Flavors)
-   **`google`**: Includes Google Play Services (Maps, Firebase, Crashlytics).
-   **`fdroid`**: FOSS version. **Strictly segregate sensitive data** (Crashlytics, Firebase, etc.) out of this flavor.
-   **Task Example:** `./gradlew assembleFdroidDebug`

## 4. Quality Assurance

### A. Code Style (Spotless)
-   The project uses **Spotless** to enforce formatting.
-   **Command:** `./gradlew spotlessApply`
-   **Rule:** You **must** run this before submitting any code.

### B. Linting (Detekt)
-   The project uses **Detekt** for static analysis.
-   **Command:** `./gradlew detekt`
-   **Rule:** Ensure zero regressions.

### C. Testing
-   **Unit Tests:** JUnit 4/5 in `src/test/java`. Run with `./gradlew test`.
-   **UI Tests:** Espresso/Compose in `src/androidTest/java`. Run with `./gradlew connectedAndroidTest`.
-   **Feature Test:** `./gradlew feature:settings:testGoogleDebug`

## 5. Agent Workflow

1.  **Explore First:** Before making changes, read `gradle/libs.versions.toml` and the relevant `build.gradle.kts` to understand the environment.
2.  **Plan:** Identify which modules (`core` or `feature`) need modification.
3.  **Implement:**
    -   If adding a string, modify `core/strings`.
    -   If adding a dependency, modify `libs.versions.toml` first.
4.  **Verify:**
    -   Run `./gradlew spotlessApply` (Essential!).
    -   Run `./gradlew detekt`.
    -   Run relevant tests (e.g., `./gradlew :feature:settings:testDebugUnitTest`).

## 6. Important Context

-   **Protobuf:** Communication with the device uses Protobufs. The definitions are in `core/proto`. This is a Git submodule, but the build system handles it.
-   **Legacy:** Some code in `app/` uses the `com.geeksville.mesh` package. Newer code in `core/` and `feature/` uses `org.meshtastic.*`. Respect the existing package structure of the file you are editing.
-   **Versioning:** Do not manually edit `versionCode` or `versionName`. These are managed by the build system and CI/CD.
-   **Database Safety:** When modifying critical database logic (e.g., `NodeInfoDao`), always ensure you have explicit test coverage for security edge cases (like PKC conflicts or key wiping). Refer to `core/database/src/androidTest/kotlin/org/meshtastic/core/database/dao/NodeInfoDaoTest.kt` for examples.

## 7. Troubleshooting

-   **Missing Strings:** If `Res.string.xyz` is unresolved, ensure you have imported `org.meshtastic.core.strings.Res` and the specific string property, and that you have run a build to generate the resources.
-   **Build Errors:** Check `gradle/libs.versions.toml` for version conflicts. Use `build-logic` conventions to ensure plugins are applied correctly.

---
*Refer to `CONTRIBUTING.md` for human-centric processes like Code of Conduct and Pull Request etiquette.*
