# `:core:network`

## Overview
The `:core:network` module handles all internet-based communication, including fetching firmware metadata, device hardware definitions, and map tiles (in the `fdroid` flavor). It also provides the shared radio transport layer (`TcpTransport`/`TcpRadioTransport`, `SerialTransport`, `BleRadioTransport`).

## Key Components

### 1. `Ktor` Client
The module uses **Ktor** as its primary HTTP client for high-performance, asynchronous networking.

### 2. Remote Data Sources
- **`FirmwareReleaseRemoteDataSource`**: Fetches the latest firmware versions from GitHub or Meshtastic's metadata servers.
- **`DeviceHardwareRemoteDataSource`**: Fetches definitions for supported Meshtastic hardware devices.

### 3. Shared Transports
- **`TcpTransport`** (`transport/`) + **`TcpRadioTransport`** (`radio/`): Multiplatform TCP transport and its `RadioTransport` adapter.
- **`SerialTransport`**: JVM-shared USB/Serial transport powered by jSerialComm.
- **`BleRadioTransport`** (`radio/`): The `RadioTransport` implementation for Bluetooth devices.
- **`BaseRadioTransportFactory`**: Common factory for instantiating the KMP transports.

> **BLE:** the `RadioTransport` implementation (`BleRadioTransport`) lives **here** in `radio/`; it delegates to the lower-level Kable connection primitives (`BleConnection`, `BleScanner`, `BluetoothRepository`, …) provided by [`:core:ble`](../ble/README.md). `BaseRadioTransportFactory` instantiates it when an address with the `x` (or `!`) prefix is resolved.


## Dependency Graph

<!--region graph-->
```mermaid
graph TB
  :core:network[network]:::kmp-library
  :core:network --> :core:repository
  :core:network -.-> :core:common
  :core:network -.-> :core:di
  :core:network -.-> :core:model
  :core:network -.-> :core:ble
  :core:network -.-> :core:testing

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
