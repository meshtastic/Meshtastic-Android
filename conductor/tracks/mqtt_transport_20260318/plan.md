# Implementation Plan: MQTT Transport

## Phase 1: Core Networking & Library Integration
- [x] Task: Evaluate and add KMP MQTT library dependency (e.g. Kmqtt) to `core:network` or `libs.versions.toml`. [2a4aa35]
    - [x] Add dependency to `libs.versions.toml`.
    - [x] Apply dependency in `core:network/build.gradle.kts`.
- [x] Task: Implement `MqttTransport` class in `commonMain` of `core:network`. [99d35b3]
    - [x] Create failing tests in `commonTest` for MqttTransport initialization and configuration parsing.
    - [x] Implement MqttTransport to parse URL (mqtt://, mqtts://), credentials, and configure the underlying MQTT client.
    - [x] Write failing tests for connection state flows.
    - [x] Implement connection lifecycle handling (connect, disconnect, reconnect).
- [x] Task: Conductor - User Manual Verification 'Phase 1: Core Networking & Library Integration' (Protocol in workflow.md) [93d9a50]

## Phase 2: Publishing & Subscribing
- [x] Task: Implement message subscription and payload parsing. [4900f69]
    - [x] Create failing tests for receiving and mapping standard Meshtastic JSON payloads from subscribed topics.
    - [x] Implement topic subscription management in `MqttTransport`.
    - [x] Implement payload parsing and integration with `core:model` definitions.
- [x] Task: Implement publishing mechanism. [0991210]
    - [x] Create failing tests for formatting and publishing node information/messages to custom topics.
    - [x] Implement publish functionality in `MqttTransport`.
- [x] Task: Conductor - User Manual Verification 'Phase 2: Publishing & Subscribing' (Protocol in workflow.md) [7418e53]

## Phase 3: Service & UI Integration
- [ ] Task: Integrate `MqttTransport` into `core:service` and `core:data`.
    - [ ] Create failing tests for orchestrating MQTT connection based on user preferences.
    - [ ] Implement service-level bindings to maintain background connection.
- [ ] Task: Implement MQTT UI Configuration Settings.
    - [ ] Create failing UI tests/snapshot tests (if applicable) for the new MQTT settings UI.
    - [ ] Add MQTT broker URL, username, password, and custom topic inputs to the UI, following Android UX patterns.
    - [ ] Wire UI inputs to `core:prefs` and the `MqttTransport` configuration state.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Service & UI Integration' (Protocol in workflow.md)