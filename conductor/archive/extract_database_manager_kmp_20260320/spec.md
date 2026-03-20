# Specification - Extract DatabaseManager to KMP

## Overview
Meshtastic-Android is designed to support per-node databases (e.g., `db_!1234abcd.db`). Currently, the logic for managing these databases (switching, LRU caching, eviction) is trapped in `core:database/androidMain`. The Desktop implementation stubs this out, forcing all nodes to share a single database, which is a major architectural regression and leads to data pollution across different devices.

This track will move the core `DatabaseManager` logic to `commonMain`, enabling full feature parity for database management on Android, Desktop, and iOS.

## Functional Requirements
- **Per-Node Databases**: Desktop and iOS must support creating and switching between separate databases based on the connected device's address.
- **LRU Eviction**: Implement an LRU (Least Recently Used) cache for database instances on all platforms.
- **Cache Limits**: The database cache limit must be configurable and respected across all platforms.
- **Legacy Cleanup**: Maintain logic for cleaning up legacy databases where applicable.

## Non-Functional Requirements
- **KMP Purity**: Use only Kotlin Multiplatform-ready libraries (`kotlinx-coroutines`, `okio`, `androidx-datastore`).
- **Dependency Injection**: Use Koin to wire the shared `DatabaseManager` into all app targets.
- **Platform Specifics**: Isolate platform-specific path resolution (e.g., Android `getDatabasePath` vs. JVM `user.home`) using the `expect`/`actual` pattern.

## Acceptance Criteria
1. `DatabaseManager` resides in `core:database/commonMain`.
2. `DesktopDatabaseManager` (the stub) is deleted.
3. Desktop creates unique database files when connecting to different nodes.
4. Unit tests in `commonTest` verify the LRU eviction logic using an Okio in-memory filesystem (or temporary test directory).
5. No `android.*` or `java.*` imports remain in the shared database management logic.
