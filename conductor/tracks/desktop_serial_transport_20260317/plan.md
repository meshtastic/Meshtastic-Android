# Implementation Plan: Desktop Serial/USB Transport

## Phase 1: JVM Setup & Dependency Integration [checkpoint: a05916d]
- [x] Task: Add the `jSerialComm` library to the `jvmMain` dependencies of the networking module. [checkpoint: 8994c66]
- [x] Task: Create a `jvmMain` stub implementation for a `SerialTransport` class that implements the shared `RadioTransport` interface. [checkpoint: 83668e4]

## Phase 2: Serial Port Scanning & Connection Management [checkpoint: 9cda87d]
- [x] Task: Implement port discovery using `jSerialComm` to list available serial ports. [checkpoint: c72501d]
- [x] Task: Implement connect/disconnect logic for a selected serial port, handling port locking and baud rate configuration. [checkpoint: 23ee815]
- [x] Task: Map the input/output streams of the open serial port to the existing KMP stream framing logic (`StreamFrameCodec`). [checkpoint: 04ba9c2]

## Phase 3: UI Integration
- [x] Task: Update the `feature:connections` UI or `DesktopScannerViewModel` to poll the new `SerialTransport` for available ports. [checkpoint: 2e85b5a]
- [x] Task: Wire the user's serial port selection to initiate the connection via the DI graph and active service logic. [checkpoint: 94cb97c]

## Phase 4: Validation [checkpoint: 1055752]
- [x] Task: Verify end-to-end communication with a physical Meshtastic device over USB on the desktop target. [checkpoint: 1055752]
- [x] Task: Ensure CI builds cleanly and that no `java.*` dependencies leaked into `commonMain`. [checkpoint: 1055752]
