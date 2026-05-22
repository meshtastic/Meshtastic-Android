# `:core:di`

## Overview

**Targets:** Android · JVM (Desktop) · iOS

The `:core:di` module defines the core Koin modules and provides standard dependencies that are shared across all other modules.

## Key Components

### 1. `AppModule.kt`
Defines bindings for application-wide singletons like `Application`, `Context`, and `Resources`.

### 2. `CoroutineDispatchers.kt`
Provides a wrapper for standard Kotlin `CoroutineDispatchers` (`IO`, `Default`, `Main`), allowing for easy mocking in unit tests.

### 3. `ProcessLifecycle.kt`
Exposes the application's global process lifecycle as a Koin binding, enabling components to react to the app entering the foreground or background.


## Dependency Graph

<!--region graph-->
<!--endregion-->
