# `:feature:wifi-provision`

## Wi-Fi Provisioning System for mPWRD-OS

The `:feature:wifi-provision` module provides BLE-based Wi-Fi provisioning for [mPWRD-OS](https://github.com/mPWRD-OS/mPWRD-OS) devices using the Nymea network manager protocol. mPWRD-OS is a community project that combines Armbian and Meshtastic for Linux-native mesh networking hardware. This module scans for provisioning-capable devices, retrieves available Wi-Fi networks, and applies credentials — all over BLE via the Kable multiplatform library.

### Architecture

- **Protocol:** Nymea BLE network manager (GATT service `e081fec0-f757-4449-b9c9-bfa83133f7fc`)
- **Transport:** BLE via `core:ble` Kable abstractions with chunked packet codec
- **UI:** Single-screen Material 3 Expressive flow with 6 phases (Idle, ConnectingBle, DeviceFound, LoadingNetworks, Connected, Provisioning)

```mermaid
sequenceDiagram
    participant App as Meshtastic App
    participant BLE as BLE Scanner
    participant Device as Provisioning Device

    Note over App: Phase 1: Scan
    App->>BLE: Scan for GATT service UUID
    BLE-->>App: Device discovered

    Note over App: Phase 2: Connect
    App->>Device: BLE Connect
    App->>App: Show device name for confirmation

    Note over App, Device: Phase 3: Network List
    App->>Device: TriggerScan command (c=4)
    App->>Device: GetNetworks command (c=0)
    Device-->>App: Wi-Fi networks (deduplicated by SSID)

    Note over App, Device: Phase 4: Provision
    App->>Device: Connect or ConnectHidden (SSID, password)
    Device-->>App: Response code, IP address when reported
    App->>Device: GetConnection (c=5) if no IP came back
```

The BLE connection stays open after provisioning, so the user can retry or pick another network; it is released when the user taps **Cancel** or leaves the screen. The flow is reached from the Android and Desktop settings screens and settings search (`WifiProvisionRoute`, registered by `wifiProvisionGraph()`).

### Key Classes

- `WifiProvisionViewModel.kt`: MVI state machine with 6 phases and SSID deduplication.
- `WifiProvisionScreen.kt`: Material 3 Expressive single-screen UI with Crossfade transitions.
- `NymeaWifiService.kt`: BLE service layer — connect, scan networks, provision, close.
- `NymeaPacketCodec.kt`: Chunked BLE packet encoder/decoder with reassembly.
- `NymeaProtocol.kt`: JSON serialization for Nymea network manager commands and responses.
- `ProvisionStatusCard.kt`: Inline status feedback card (idle/success/failed) with Material 3 colors.
- `NymeaBleConstants.kt`: The nymea GATT service and characteristic UUIDs.

## Testing

`./gradlew :feature:wifi-provision:allTests` covers the packet codec, the protocol JSON, the service against a fake BLE connection, SSID deduplication, and the ViewModel.

## Dependency Graph

<!--region graph-->
```mermaid
graph TB
  :feature:wifi-provision[wifi-provision]:::kmp-feature
  :feature:wifi-provision -.-> :core:ble
  :feature:wifi-provision -.-> :core:common
  :feature:wifi-provision -.-> :core:di
  :feature:wifi-provision -.-> :core:model
  :feature:wifi-provision -.-> :core:navigation
  :feature:wifi-provision -.-> :core:resources
  :feature:wifi-provision -.-> :core:ui
  :feature:wifi-provision -.-> :core:testing

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
