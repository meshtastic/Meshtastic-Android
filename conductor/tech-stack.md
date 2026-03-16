# Tech Stack

## Programming Language
- **Kotlin Multiplatform (KMP):** The core logic is shared across Android, Desktop, and iOS using `commonMain`.

## Frontend Frameworks
- **Compose Multiplatform:** Shared UI layer for rendering on Android and Desktop.
- **Jetpack Compose:** Used where platform-specific UI (like charts or permissions) is necessary on Android.

## Architecture
- **MVI / Unidirectional Data Flow:** Shared view models using the multiplatform `androidx.lifecycle.ViewModel`.
- **JetBrains Navigation 3:** Multiplatform fork for state-based, compose-first navigation without relying on `NavController`.

## Dependency Injection
- **Koin 4.2:** Leverages Koin Annotations and the K2 Compiler Plugin for pure compile-time DI, completely replacing Hilt.

## Database & Storage
- **Room KMP:** Shared local database using multiplatform `DatabaseConstructor`.
- **Jetpack DataStore:** Shared preferences.

## Networking & Transport
- **Ktor:** Multiplatform HTTP client for web services and TCP streaming.
- **Kable:** Multiplatform BLE library used as the primary BLE transport for all targets (Android, Desktop, and future iOS).
- **Coroutines & Flows:** For asynchronous programming and state management.