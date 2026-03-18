# Specification: MQTT Transport

## Overview
Implement an MQTT transport layer for the Meshtastic-Android Kotlin Multiplatform (KMP) application to enable communication with Meshtastic devices over MQTT. This will support Android, Desktop, iOS, and potentially Web platforms in the future.

## Functional Requirements
- **Platforms:** Ensure the MQTT transport operates correctly across Android, Desktop, and iOS platforms, using KMP best practices (with considerations for Web compatibility if technically feasible).
- **Core Library:** Utilize a dedicated Kotlin Multiplatform MQTT client library (e.g., Kmqtt) within the `core:network` module.
- **Connection Features:**
  - Support for both standard (`mqtt://`) and secure TLS/SSL (`mqtts://`) connections.
  - Support for username and password authentication.
- **Messaging Features:**
  - Subscribe to and publish on user-defined custom topics.
  - Parse and serialize standard Meshtastic JSON payloads.
- **UI Integration:**
  - Follow the existing Android UX patterns for network/device connections.
  - Integrate MQTT configuration seamlessly into the connection or advanced settings menus.

## Non-Functional Requirements
- **Architecture:** Business logic for MQTT communication must reside in the `core:network` (or a new `core:mqtt`) `commonMain` source set.
- **Testability:** Implement shared tests in `commonTest` to verify connection states, topic parsing, and payload serialization without relying on JVM-specific mocks.
- **Performance:** Ensure background execution and resource management align with the `core:service` architecture.

## Acceptance Criteria
- [ ] Users can enter an MQTT broker URL (including TLS), username, and password in the UI.
- [ ] The app successfully connects to the specified MQTT broker and maintains the connection in the background.
- [ ] The app can publish Meshtastic node information/messages to the broker.
- [ ] The app can receive and process incoming Meshtastic payloads from subscribed topics.
- [ ] Unit tests cover at least 80% of the new MQTT client logic.

## Out of Scope
- Direct firmware updates via MQTT (if not natively supported by the standard payload).
- Implementing a full local MQTT broker on the device.