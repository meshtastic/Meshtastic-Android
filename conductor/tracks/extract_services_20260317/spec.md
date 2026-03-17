# Specification: Extract service/worker/radio files from `app`

## Overview
This track aims to decouple the main `app` module by extracting Android-specific service, WorkManager worker, and radio connection files into `core:service` and `core:network` modules. The goal is to maximize code reuse across Kotlin Multiplatform (KMP) targets, clarify class responsibilities, and improve unit testability by isolating the network and service layers.

## Goals
- **Decouple `app`:** Remove Android-specific service dependencies from the main app module.
- **KMP Preparation:** Migrate as much logic as possible into `commonMain` for reuse across platforms.
- **Desktop Integration:** If logic is successfully abstracted into `commonMain`, integrate and use it within the `desktop` target to ensure reusability.
- **Testability:** Isolate service and network layers to facilitate better unit testing.
- **Simplification:** Refactor logic during the move to clarify and simplify responsibilities.

## Functional Requirements
- Identify all service, worker, and radio-related classes currently residing in the `app` module.
- Move Android-specific implementations (e.g., `Service`, `Worker`) to `core:service/androidMain` and `core:network/androidMain`.
- Extract platform-agnostic business logic and interfaces into `commonMain` within those core modules.
- Refactor existing logic where necessary to establish a clear delineation of responsibility.
- Update all dependency injections (Koin modules) and imports across the project to reflect the new locations.
- Attempt to wire up the newly abstracted shared logic within the `desktop` module if applicable.

## Non-Functional Requirements
- **Architecture Compliance:** Changes must adhere to the MVI / Unidirectional Data Flow and KMP structures defined in `tech-stack.md`.
- **Performance:** Refactoring should not negatively impact app startup time or background processing efficiency.
- **Code Coverage:** Maintain or improve overall test coverage for the extracted components (>80% target).

## Acceptance Criteria
- [ ] No service, worker, or radio connection classes remain in the `app` module.
- [ ] Extracted Android-specific classes compile successfully in `core:service/androidMain` and `core:network/androidMain`.
- [ ] Shared business logic compiles successfully in `core:service/commonMain` and `core:network/commonMain`.
- [ ] If logic is abstracted for reuse, it is integrated and utilized in the `desktop` target where applicable.
- [ ] The app compiles, installs, and runs without regressions in background processing or radio connectivity.
- [ ] Unit tests for the moved and refactored classes pass.