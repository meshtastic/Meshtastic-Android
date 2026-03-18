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
- [ ] Task: Conductor - User Manual Verification 'Phase 1: Core Networking & Library Integration' (Protocol in workflow.md)

## Phase 2: Publishing & Subscribing
- [ ] Task: Implement message subscription and payload parsing.
    - [ ] Create failing tests for receiving and mapping standard Meshtastic JSON payloads from subscribed topics.
    - [ ] Implement topic subscription management in `MqttTransport`.
    - [ ] Implement payload parsing and integration with `core:model` definitions.
- [ ] Task: Implement publishing mechanism.
    - [ ] Create failing tests for formatting and publishing node information/messages to custom topics.
    - [ ] Implement publish functionality in `MqttTransport`.
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Publishing & Subscribing' (Protocol in workflow.md)

## Phase 3: Service & UI Integration
- [ ] Task: Integrate `MqttTransport` into `core:service` and `core:data`.
    - [ ] Create failing tests for orchestrating MQTT connection based on user preferences.
    - [ ] Implement service-level bindings to maintain background connection.
- [ ] Task: Implement MQTT UI Configuration Settings.
    - [ ] Create failing UI tests/snapshot tests (if applicable) for the new MQTT settings UI.
    - [ ] Add MQTT broker URL, username, password, and custom topic inputs to the UI, following Android UX patterns.
    - [ ] Wire UI inputs to `core:prefs` and the `MqttTransport` configuration state.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Service & UI Integration' (Protocol in workflow.md)