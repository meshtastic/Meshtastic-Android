# Specification: Extract Android Navigation graphs to feature modules for app thinning

## Overview
The primary goal of this track is to thin out the app module by moving the Android-specific navigation graph wiring (e.g., SettingsNavigation.kt, NodesNavigation.kt, ConnectionsNavigation.kt) into their respective feature modules (e.g., feature:settings, feature:node, feature:connections). This aligns the Android implementation with the Desktop application's architecture, where navigation logic is collocated with the features it routes.

## Functional Requirements
- **Target Modules:** Move all feature-specific navigation files from `app/src/main/kotlin/org/meshtastic/app/navigation/` to the `androidMain` source sets of their corresponding `feature:*` modules.
- **Architecture:** Implement JetBrains Navigation 3 best practices for common usage across KMP modules. This involves ensuring the feature modules expose their navigation graphs seamlessly to the root NavHost in the app module, minimizing tight coupling.
- **Root App Shell:** The app module should only retain the root MainActivity, the root DI graph assembly, and the top-level NavHost (e.g., MeshtasticApp.kt or similar entry point), calling into the feature modules' exposed graph functions.

## Non-Functional Requirements
- **Testability:** Add or update tests to verify that the complete navigation graph is correctly assembled from the individual feature modules without errors.
- **Maintainability:** The extraction must preserve all existing deep links, arguments, and navigation transitions currently defined in the Android app.

## Acceptance Criteria
- [ ] The `app/src/main/kotlin/org/meshtastic/app/navigation/` directory only contains the root graph assembly.
- [ ] All Android feature navigation graphs are successfully extracted to their respective `feature:*` modules.
- [ ] The Android app compiles and runs successfully, with all navigation flows working identically to the previous implementation.
- [ ] New graph assembly tests are added and pass in CI/local environments.