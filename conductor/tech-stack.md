# Tech Stack

## Programming Language
- **Kotlin Multiplatform (KMP):** The core logic is shared across Android, Desktop, and iOS using `commonMain`.

## Frontend Frameworks
- **Compose Multiplatform:** Shared UI layer for rendering on Android and Desktop.
- **Jetpack Compose:** Used where platform-specific UI (like charts or permissions) is necessary on Android.

## Background & Services
- **Platform Services:** Core service orchestrations and background work are abstracted into `core:service` to maximize logic reuse across targets, using platform-specific implementations (e.g., WorkManager/Service on Android) only where necessary.

## Architecture
- **MVI / Unidirectional Data Flow:** Shared view models using the multiplatform `androidx.lifecycle.ViewModel`.
- **JetBrains Navigation 3:** Multiplatform fork for state-based, compose-first navigation without relying on `NavController`. Navigation graphs are decoupled and extracted into their respective `feature:*` modules, allowing a thinned out root `app` module.

## Dependency Injection
- **Koin 4.2:** Leverages Koin Annotations and the K2 Compiler Plugin for pure compile-time DI, completely replacing Hilt.

## Database & Storage
- **Room 3 KMP:** Shared local database using multiplatform `DatabaseConstructor` and the `androidx.sqlite` bundled driver across Android, Desktop, and iOS.
- **Jetpack DataStore:** Shared preferences.

## Networking & Transport
- **Ktor:** Multiplatform HTTP client for web services and TCP streaming.
- **Kable:** Multiplatform BLE library used as the primary BLE transport for all targets (Android, Desktop, and future iOS).
- **jSerialComm:** Cross-platform Java library used for direct Serial/USB communication with Meshtastic devices on the Desktop (JVM) target.
- **KMQTT:** Kotlin Multiplatform MQTT client and broker used for MQTT transport, replacing the Android-only Paho library.
- **Coroutines & Flows:** For asynchronous programming and state management.

## Testing (KMP)
- **Shared Tests First:** The majority of business logic, ViewModels, and state interactions are tested in the `commonTest` source set using standard `kotlin.test`.
- **Coroutines Testing:** Use `kotlinx-coroutines-test` for virtual time management in asynchronous flows.
- **Mocking Strategy:** Avoid JVM-specific mocking libraries. Prefer `Mokkery` or `Mockative` for multiplatform-compatible mocking interfaces, alongside handwritten fakes in `core:testing`.
- **Flow Assertions:** Use `Turbine` for testing multiplatform `Flow` emissions and state updates.
- **Property-Based Testing:** Use `Kotest` for multiplatform data-driven and property-based testing scenarios.