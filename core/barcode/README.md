# `:core:barcode`

## Overview
The `:core:barcode` module provides barcode and QR code scanning capabilities using Google ML Kit and CameraX. It is used for scanning node configuration, pairing, and contact sharing.

## Key Components

### 1. `BarcodeScanner`
A Composable component that provides a live camera preview and detects barcodes/QR codes in real-time.

- **Technology:** Uses **CameraX** for camera lifecycle management and **ML Kit Barcode Scanning** for detection.
- **Flavors:** Uses the bundled ML Kit library to ensure consistent performance across both `google` and `fdroid` flavors without depending on Google Play Services.

### 2. `BarcodeUtil`
Utility functions for generating and parsing Meshtastic-specific QR codes (e.g., node URLs).

## Usage
The module exposes a scanner that can be integrated into any Compose screen.

```kotlin
BarcodeScanner(
    onBarcodeDetected = { barcode ->
        // Handle scanned barcode
    },
    onDismiss = {
        // Handle dismiss
    }
)
```

## Module dependency graph

<!--region graph-->
```mermaid
graph TB
  :core:barcode[barcode]:::android-library
  :core:barcode -.-> :core:resources
  :core:barcode -.-> :core:ui

classDef android-application fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef android-application-compose fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef android-feature fill:#FFD6A5,stroke:#000,stroke-width:2px,color:#000;
classDef android-library fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
classDef android-library-compose fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
classDef android-test fill:#A0C4FF,stroke:#000,stroke-width:2px,color:#000;
classDef jvm-library fill:#BDB2FF,stroke:#000,stroke-width:2px,color:#000;
classDef kmp-library fill:#FFC1CC,stroke:#000,stroke-width:2px,color:#000;
classDef unknown fill:#FFADAD,stroke:#000,stroke-width:2px,color:#000;

```
<!--endregion-->
