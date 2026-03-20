# Implementation Plan - Room 3 Migration

## Phase 1: Dependency Update & Build Logic Refinement
- Update `libs.versions.toml` to Room 3.0.
- Update `AndroidRoomConventionPlugin.kt` to align with Room 3 best practices (e.g., ensuring `room.generateKotlin` is correctly set and using the `androidx.room` Gradle plugin).
- Verify all modules (`core:database`, `core:data`, `app`, etc.) can build with the new dependencies.
- [x] Task: Update `libs.versions.toml` with Room 3.0 and related dependencies.
- [x] Task: Refactor `AndroidRoomConventionPlugin.kt` for Room 3.0.
- [x] Task: Conductor - User Manual Verification 'Phase 1' (Protocol in workflow.md)

## Phase 2: Core Database Implementation (KMP)
- Refactor `MeshtasticDatabase.kt` and `MeshtasticDatabaseConstructor.kt` to use the new Room 3 `RoomDatabase.Builder` for KMP.
- Configure the `BundledSQLiteDriver` in `commonMain` to ensure consistent SQL behavior across all targets.
- Ensure that DAOs and Entities are using `room-runtime` in `commonMain` correctly.
- Implement platform-specific database setup for Android, Desktop, and iOS in their respective `Main` source sets.
- [x] Task: Refactor `MeshtasticDatabase.kt` for Room 3.0 KMP APIs.
- [x] Task: Configure `BundledSQLiteDriver` in `DatabaseProvider.kt`.
- [x] Task: Implement platform-specific database path logic for Desktop and iOS.
- [x] Task: Conductor - User Manual Verification 'Phase 2' (Protocol in workflow.md)

## Phase 3: Multi-target Support (iOS)
- Add iOS targets (`iosX64`, `iosArm64`, `iosSimulatorArm64`) to `core:database/build.gradle.kts`.
- Configure the database file path logic for iOS.
- Verify that the `core:database` module compiles for iOS.
- [x] Task: Add iOS targets to `core:database/build.gradle.kts`.
- [x] Task: Verify iOS compilation (Skipped: Linux host).
- [x] Task: Conductor - User Manual Verification 'Phase 3' (Protocol in workflow.md)

## Phase 4: Verification and Testing
- Update existing database tests in `commonTest`, `androidHostTest`, and `androidDeviceTest` to Room 3.
- Run tests on Android and Desktop to ensure no regressions in behavior.
- Perform manual verification on Android and Desktop apps to ensure the database initializes and functions correctly.
- [x] Task: Update and run DAO unit tests in `commonTest`.
- [x] Task: Run Android instrumented tests (`androidDeviceTest`).
- [x] Task: Manual verification on Desktop.
- [x] Task: Conductor - User Manual Verification 'Phase 4' (Protocol in workflow.md)
