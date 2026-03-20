# Specification - Extract RadioInterfaceService to KMP

## Overview
Currently, the connection orchestration logic for establishing, monitoring, and tearing down connections with Meshtastic radios is duplicated. Android uses `AndroidRadioInterfaceService` in `core:service/androidMain`, and Desktop uses `DesktopRadioInterfaceService` in the `desktop` module. This duplicates core state management (connecting, connected, disconnecting) and the interactions with the shared `TcpTransport`, `SerialTransport`, and `BleTransport`.

This track aims to abstract the remaining platform-specific connection logic (if any) and move the bulk of `RadioInterfaceService` into `core:repository/commonMain` or `core:service/commonMain`, unifying the connection lifecycle across all targets.

## Functional Requirements
- **Unified Connection Lifecycle**: A single `RadioInterfaceService` implementation in `commonMain` should handle connection state management (connecting, active, disconnect, reconnect loops).
- **Transport Abstraction**: The service must interact with connections via a multiplatform interface, presumably standardizing around `RadioTransport` or `ConnectionFactory`.
- **Platform Parity**: Desktop and Android must use the exact same logic for detecting disconnects and issuing reconnects.

## Non-Functional Requirements
- **KMP Purity**: The unified service must not depend on `android.*` or `java.*` specific APIs for its core lifecycle management.
- **Dependency Injection**: Utilize Koin in `commonMain` to provide the unified service.

## Acceptance Criteria
1. `DesktopRadioInterfaceService` is removed.
2. `AndroidRadioInterfaceService` is replaced by a shared implementation in `commonMain` (e.g., `SharedRadioInterfaceService`).
3. Both Android and Desktop can successfully connect, disconnect, and handle unexpected drops using the shared logic.
