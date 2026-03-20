# Implementation Plan - Extract DatabaseManager to KMP

## Phase 1: Multiplatform Database Abstraction
- [x] Define `expect fun buildRoomDb(dbName: String): MeshtasticDatabase` in `commonMain`.
- [x] Implement `actual fun buildRoomDb` for Android (using `Application.getDatabasePath`).
- [x] Implement `actual fun buildRoomDb` for JVM/Desktop (using the established `~/.meshtastic` data directory).
- [x] Implement `actual fun buildRoomDb` for iOS (using `NSDocumentDirectory`).
- [x] Update `DatabaseConstants` with shared keys and default values.

## Phase 2: KMP DataStore & File I/O
- [x] Replace Android `SharedPreferences` in `DatabaseManager` with a KMP-ready `DataStore<Preferences>` instance named `DatabasePrefs`.
- [x] Introduce an `expect fun deleteDatabase(dbName: String)` or similar Okio-based deletion helper.
- [x] Refactor database file listing to use `okio.FileSystem.SYSTEM` instead of `java.io.File`.

## Phase 3: Logic Extraction
- [x] Move `DatabaseManager.kt` from `core:database/androidMain` to `core:database/commonMain`.
- [x] Refactor `DatabaseManager` to use the new `buildRoomDb`, `DataStore`, and `FileSystem` abstractions.
- [x] Ensure `DatabaseManager` is annotated with Koin `@Single` and correctly binds to `DatabaseProvider` and `SharedDatabaseManager` (from `core:common`).
- [x] Remove `DesktopDatabaseManager` from `desktop` module.
- [x] Update the DI (Koin) graph in `app` and `desktop` to wire the new shared `DatabaseManager`.

## Phase 4: Verification
- [x] Add unit tests in `core:database/commonTest` to verify that `switchActiveDatabase` correctly swaps databases and that the LRU eviction limit is respected.
- [x] Perform manual verification on Desktop to ensure that connecting to different nodes creates separate `.db` files in `~/.meshtastic/`.
- [x] Verify that the `core:database` module still compiles for Android and iOS targets.
