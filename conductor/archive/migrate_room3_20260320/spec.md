# Specification - Room 3 Migration

## Overview
Migrate the existing database implementation from Room 2.8.x to Room 3.0. This migration aims to modernize the persistence layer by adopting Room's new Kotlin Multiplatform (KMP) capabilities, ensuring consistent behavior across Android, Desktop (JVM), and iOS targets. Following best practice from reference projects.

## Functional Requirements
- **Room 3.0 Update**: Update all Room-related dependencies to version 3.0 (alpha/beta/stable as per latest).
- **KMP Support**: Ensure `core:database` is fully compatible with Android, Desktop (JVM), and iOS targets.
- **Bundled SQLite Driver**: Configure the project to use the `androidx.sqlite:sqlite-bundled` driver for all platforms to ensure consistent SQL behavior and versioning.
- **Schema Management**: Maintain existing database schemas and ensure migrations (if any) are compatible with Room 3.
- **DAO & Entity Optimization**: Refactor DAOs and Entities to use Room 3's idiomatic Kotlin APIs (e.g., using `RoomDatabase.Builder` for KMP).

## Non-Functional Requirements
- **Performance**: Ensure no significant regression in database performance after the migration.
- **Reliability**: All existing database tests must pass on Android.
- **Maintainability**: Adopt the new Room Gradle plugin for schema export and generation.

## Acceptance Criteria
1.  All modules (`core:database`, `core:data`, etc.) build successfully with Room 3.0.
2.  Database initialization works correctly on Android and Desktop.
3.  Unit tests for DAOs pass in `commonTest` (where applicable) and `androidDeviceTest`.
4.  The `androidx.sqlite:sqlite-bundled` driver is used for database connections.
5.  iOS target is added to `core:database` (if not already present) and compiles.

## Out of Scope
- Migrating to a different database engine (e.g., SQLDelight).
- Major schema changes unrelated to the Room 3 migration.
- Implementing complex iOS-specific UI related to the database.
