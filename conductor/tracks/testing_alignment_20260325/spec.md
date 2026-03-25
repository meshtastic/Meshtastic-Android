# Specification: KMP Testing Best Practices & Coverage Alignment

## Overview
This track aims to standardize testing across the Meshtastic-Android codebase by aligning all modules with KMP testing best practices. This includes migrating tests to `commonTest`, utilizing and expanding `core:testing` fakes, and achieving a target of 80% code coverage per module using Kover.

## Functional Requirements
-   **KMP Migration:** Relocate unit tests from `androidMain` or `jvmMain` to `commonMain` (`commonTest` source set) wherever the logic is platform-agnostic.
-   **Standardized Fakes:** Replace existing mocks or concrete implementations in tests with standardized fakes from `core:testing`.
-   **New Fakes Implementation:** Identify missing fakes for core components (Repositories, Managers, Services) and implement them in `core:testing` to ensure consistency and testability across all platforms.
-   **Coverage Targets:** Each module should target 80% line coverage.
-   **Kover Integration:** Use Kover reports to verify coverage levels.

## Non-Functional Requirements
-   **Consistency:** All tests should follow the same pattern (MVI/UDF for ViewModels, fake-driven for repositories).
-   **Performance:** Tests should remain fast and reliable. Avoid expensive integrations in unit tests.
-   **Maintainability:** Centralizing fakes in `core:testing` should reduce duplication and simplify test maintenance.

## Acceptance Criteria
-   All `core` and `feature` modules have tests in `commonTest` for platform-agnostic logic.
-   `core:testing` contains fakes for all major repository and manager interfaces.
-   Kover reports show >= 80% coverage for the prioritized modules.
-   All tests pass on both JVM and Android targets.

## Out of Scope
-   UI/Compose instrumentation tests (unless specifically requested for a critical path).
-   Performance benchmarking tests.
-   Integration tests with real hardware/radios.