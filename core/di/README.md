# `:core:di`

## Overview

**Targets:** Android · JVM (Desktop) · iOS

The `:core:di` module defines the core Koin modules and provides standard dependencies that are shared across all other modules.

## Key Components

### 1. `CoroutineDispatchers.kt`
A small data class wrapping the standard coroutine dispatchers (`io`, `main`, `default`), allowing injected classes to swap in test dispatchers instead of hard-coding `Dispatchers.*`.

### 2. `di/CoreDiModule.kt`
The Koin `@Module` for this module. Provides the `CoroutineDispatchers` singleton — `main`/`default` from `kotlinx.coroutines.Dispatchers`, `io` from `:core:common`'s platform-aware `ioDispatcher`.


## Dependency Graph

<!--region graph-->
```mermaid
graph TB
  :core:di[di]:::kmp-library
  :core:di -.-> :core:common

classDef android-application fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef android-application-compose fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef compose-desktop-application fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef android-feature fill:#FFD6A5,stroke:#000,stroke-width:2px,color:#000;
classDef android-library fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
classDef android-library-compose fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
classDef android-test fill:#A0C4FF,stroke:#000,stroke-width:2px,color:#000;
classDef jvm-library fill:#BDB2FF,stroke:#000,stroke-width:2px,color:#000;
classDef kmp-feature fill:#FFD6A5,stroke:#000,stroke-width:2px,color:#000;
classDef kmp-library-compose fill:#FFC1CC,stroke:#000,stroke-width:2px,color:#000;
classDef kmp-library fill:#FFC1CC,stroke:#000,stroke-width:2px,color:#000;
classDef unknown fill:#FFADAD,stroke:#000,stroke-width:2px,color:#000;

```
<!--endregion-->
