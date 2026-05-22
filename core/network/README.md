# `:core:network`

## Overview
The `:core:network` module handles all internet-based communication, including fetching firmware metadata, device hardware definitions, and map tiles (in the `fdroid` flavor). It also provides the shared radio transport layer (`TCPInterface`, `SerialTransport`, `BleRadioInterface`).

## Key Components

### 1. `Ktor` Client
The module uses **Ktor** as its primary HTTP client for high-performance, asynchronous networking.

### 2. Remote Data Sources
- **`FirmwareReleaseRemoteDataSource`**: Fetches the latest firmware versions from GitHub or Meshtastic's metadata servers.
- **`DeviceHardwareRemoteDataSource`**: Fetches definitions for supported Meshtastic hardware devices.

### 3. Shared Transports
- **`TCPInterface`**: Multiplatform TCP transport.
- **`SerialTransport`**: JVM-shared USB/Serial transport powered by jSerialComm.
- **`BaseRadioTransportFactory`**: Common factory for instantiating the KMP transports.

> **BLE transport** lives in [`:core:ble`](../ble/README.md), not here. `BaseRadioTransportFactory` delegates to it when an address with the `x` prefix is resolved.


## Dependency Graph

<!--region graph-->
<!--endregion-->
