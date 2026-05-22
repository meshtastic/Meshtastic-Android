# `:core:nfc`

## Overview
The `:core:nfc` module provides Near Field Communication (NFC) capabilities for the application. It is a KMP module with Android NFC hardware implementation isolated to `androidMain`. The shared NFC contract is provided via `LocalNfcScannerProvider` in `core:ui`.

## Key Components

### 1. `NfcScannerEffect` (androidMain)
A Composable side-effect that manages Android NFC adapter state and listens for NDEF tags. Located in `androidMain` since NFC hardware APIs are Android-specific.

### 2. `LocalNfcScannerProvider` (core:ui/commonMain)
The shared capability contract for NFC scanning, injected via `CompositionLocalProvider` from the app layer.


## Dependency Graph

<!--region graph-->
<!--endregion-->
