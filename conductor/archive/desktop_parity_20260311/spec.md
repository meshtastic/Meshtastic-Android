# Track Specification: Desktop Parity & Multi-Target Hardening

## Overview
This track aims to bring the Desktop target up to parity with the Android app and lay the foundation for future targets (like iOS). This involves eliminating duplicated code, fixing structural gaps, and sharing UI, navigation, and DI contracts across platforms.

## Functional Requirements
- **Connections Parity:** Consolidate device discovery (BLE/USB/TCP) from the app and desktop into a shared `feature:connections` module.
- **DI Parity:** Remove manual ViewModel wiring in `DesktopKoinModule` and transition to using KSP-generated Koin modules for Desktop.
- **UI/Feature Parity:** Implement missing map and charting functionality on Desktop, or provide robust KMP abstractions where direct translation isn't possible.
- **Navigation Parity:** Extract shared navigation contracts to stop drift between Android and Desktop shells (following `decisions/navigation3-parity-2026-03.md`).

## Non-Functional Requirements
- **Architecture Readiness:** Ensure code abstractions support the subsequent addition of an iOS target.
- **Structural Purity:** `commonMain` must be completely free of platform-specific APIs (like `java.*` or Android-specific APIs).

## Acceptance Criteria
- Device discovery screens share UI and view models in `feature:connections`.
- Desktop DI uses generated modules without manual ViewModel instantiation.
- Map and charting features are either functioning on Desktop or have solid KMP placeholders.
- Android and Desktop Navigation shells utilize shared configuration and metadata.
- Both functional and structural parity goals are verified through automated builds and testing where applicable.

## Out of Scope
- Full deployment to iOS or other unannounced platforms (only preparing the architecture).
- Deep refactoring of underlying hardware interactions beyond what is necessary to expose a shared UI contract.