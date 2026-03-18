# Specification: Desktop Serial/USB Transport via jSerialComm

## Objective
Implement direct radio connection via Serial/USB on the Desktop (JVM) target using the `jSerialComm` library. This fulfills the medium-term priority of bringing physical transport parity to the desktop app and validates the newly extracted `RadioTransport` abstraction in `core:repository`.

## Background
Currently, the desktop app supports TCP connections via a shared `StreamFrameCodec`. To provide parity with Android's USB serial connection capabilities, we need to implement a JVM-specific serial transport. The `jSerialComm` library is a widely-used, cross-platform Java library that handles native serial port communication without requiring complex JNI setups.

## Requirements
- Introduce `jSerialComm` dependency to the `jvmMain` source set of the appropriate core module (likely `core:network` or a new `core:serial` module).
- Implement the `RadioTransport` interface (defined in `core:repository/commonMain`) for the desktop target, wrapping `jSerialComm`'s port scanning and connection logic.
- Ensure the serial data is encoded/decoded using the same protobuf frame structure utilized by the TCP transport (e.g., leveraging the existing `StreamFrameCodec`).
- Integrate the new transport into the `feature:connections` UI on the desktop so users can scan for and select connected USB serial devices.
- Retain platform purity: keep all `jSerialComm` and `java.io.*` imports strictly within the `jvmMain` source set. 

## Success Criteria
- [ ] Desktop application successfully scans for connected Meshtastic devices over USB/Serial.
- [ ] Users can select a serial port from the `feature:connections` UI and establish a connection.
- [ ] Two-way protobuf communication is verified (e.g., the app receives node info and can send a message).
- [ ] The implementation uses the shared `RadioTransport` interface without leaking JVM dependencies into `commonMain`.
