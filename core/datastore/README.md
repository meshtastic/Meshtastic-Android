# `:core:datastore`

## Overview

**Targets:** Android · JVM (Desktop) · iOS

The `:core:datastore` module manages structured, asynchronous data storage using **Jetpack DataStore**. It is primarily used for storing complex configuration objects like radio channel sets and local device configurations.

## Key Components

### 1. Data Sources
- **`ChannelSetDataSource`**: Manages the storage of radio channel configurations.
- **`LocalConfigDataSource`** / **`ModuleConfigDataSource`**: Store the connected device's `LocalConfig` and `LocalModuleConfig` protos.
- **`LocalStatsDataSource`**: Stores the latest local device statistics telemetry.
- **`RecentAddressesDataSource`**: Stores a list of recently connected radio addresses (BLE/USB/TCP).
- **`BootloaderWarningDataSource`** / **`FirmwareRecoveryDataSource`**: Persist firmware-update safety state (bootloader warnings, pending recovery).

> UI preferences live in [`:core:prefs`](../prefs/README.md) (`UiPrefs` / `UiPrefsImpl`), not here.

### 2. Serializers
Uses **Kotlin Serialization** to convert between Protobuf/JSON and the underlying DataStore storage.


## Dependency Graph

<!--region graph-->
```mermaid
graph TB
  :core:datastore[datastore]:::kmp-library
  :core:datastore -.-> :core:common
  :core:datastore -.-> :core:model

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
