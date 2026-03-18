# Implementation Plan: Desktop Serial/USB Transport

## Phase 1: JVM Setup & Dependency Integration [checkpoint: a05916d]
- [x] Task: Add the `jSerialComm` library to the `jvmMain` dependencies of the networking module. [checkpoint: 8994c66]
- [x] Task: Create a `jvmMain` stub implementation for a `SerialTransport` class that implements the shared `RadioTransport` interface. [checkpoint: 83668e4]

## Phase 2: Serial Port Scanning & Connection Management
- [ ] Task: Implement port discovery using `jSerialComm` to list available serial ports.
- [ ] Task: Implement connect/disconnect logic for a selected serial port, handling port locking and baud rate configuration.
- [ ] Task: Map the input/output streams of the open serial port to the existing KMP stream framing logic (`StreamFrameCodec`).

## Phase 3: UI Integration
- [ ] Task: Update the `feature:connections` UI or `DesktopScannerViewModel` to poll the new `SerialTransport` for available ports.
- [ ] Task: Wire the user's serial port selection to initiate the connection via the DI graph and active service logic.

## Phase 4: Validation
- [ ] Task: Verify end-to-end communication with a physical Meshtastic device over USB on the desktop target.
- [ ] Task: Ensure CI builds cleanly and that no `java.*` dependencies leaked into `commonMain`.
