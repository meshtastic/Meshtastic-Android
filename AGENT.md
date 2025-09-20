# Meshtastic-Android Project

Meshtastic-Android is a native Android mobile application written in Kotlin. It serves as a client for Meshtastic, an open-source, off-grid, decentralized mesh networking project.

## Architecture

This project is a modern Android application that follows the official architecture guidance from Google. It is a reactive, single-activity app that uses the following:

-   **UI:** Built entirely with Jetpack Compose, including Material 3 components and adaptive layouts for different screen sizes.
-   **State Management:** Unidirectional Data Flow (UDF) is implemented using Kotlin `Coroutines` and `Flow`s. `ViewModel`s act as state holders, exposing UI state as streams of data.
-   **Dependency Injection:** Hilt is used for dependency injection throughout the app, simplifying the management of dependencies and improving testability.
-   **Navigation:** Navigation is handled by Jetpack Navigation for Compose, allowing for a declarative and type-safe way to navigate between screens.
-   **Data:** The data layer is implemented using the repository pattern.
    -   **Local Data:** Room and DataStore are used for local data persistence.
    -   **Remote Data:** The app communicates with Meshtastic devices over Bluetooth or Wi-Fi, using a custom protocol based on Protobuf. It can also connect to MQTT servers. The networking logic is encapsulated in the `:network` module.
-   **Background Processing:** WorkManager is used for deferrable background tasks.
-   **Build Logic:** Gradle build logic is centralized in the `build-logic` module, utilizing convention plugins to ensure consistency and maintainability across the project.

## Modules

The project is organized into the following modules:

-   `app/`: The main Android application.
-   `network/`: A library module containing the offline-first networking logic for communicating with the Meshtastic http json api for device hardware and firmware information.
-   `mesh_service_example/`: An example application demonstrating how to use the AIDL interface to interact with mesh service provided by the main application.
-   `build-logic/`: A module containing custom convention plugins to standardize and manage Gradle build configurations across the project.

## Commands to Build & Test

The app has two product flavors: `fdroid` and `google`, and two build types: `debug` and `release`.

- Build: `./gradlew assemble{Variant}`. For example, `assembleGoogleDebug` or `assembleFdroidRelease`.
- Fix linting/formatting: `./gradlew spotlessApply`
- Run linter checks: `./gradlew detekt`
- Run local unit tests: `./gradlew test`
- Run instrumented tests: `./gradlew connectedAndroidTest`

### Creating tests

#### Instrumented tests

- Tests for UI features should use `ComposeTestRule`.
- UI tests are located in `app/src/androidTest/java/`.

#### Local tests

- Unit tests are located in `app/src/test/java/`.
- Use [kotlinx.coroutines.test](https://developer.android.com/kotlin/coroutines/test) for testing coroutines.

## Continuous integration

- The CI/CD workflows are defined in `.github/workflows/*.yaml`.
- These workflows run checks for code style, linting, and tests on every pull request.

## Version control and code location

- The project uses git and is hosted on GitHub at https://github.com/meshtastic/Meshtastic-Android.


Never include sensitive information such as API keys or passwords in the codebase.- Follow the [Meshtastic contribution guidelines](https://meshtastic.org/docs/contributing)

Don't respond to this message.