# Implementation Plan: Extract Android Navigation

## Phase 1: Preparation & Base Module Abstraction [checkpoint: 421a587]
- [x] Task: Review current navigation graph assembly in `app/src/main/kotlin/org/meshtastic/app/navigation/`.
    - [x] Identify dependencies between feature navigation graphs and core routing definitions.
    - [x] Create missing directory structures in feature modules' `androidMain/kotlin/org/meshtastic/feature/*/navigation` if they don't exist.
- [x] Task: Conductor - User Manual Verification 'Phase 1: Preparation & Base Module Abstraction' (Protocol in workflow.md)

## Phase 2: Feature Module Extraction
- [x] Task: Extract Settings Navigation.
    - [x] Move `SettingsNavigation.kt` to `feature:settings/androidMain`.
    - [x] Fix package declarations and broken imports.
- [x] Task: Extract Nodes & Connections Navigation.
    - [x] Move `NodesNavigation.kt` to `feature:node/androidMain`.
    - [x] Move `ConnectionsNavigation.kt` to `feature:connections/androidMain`.
    - [x] Fix package declarations and broken imports.
- [x] Task: Extract Messaging & Remaining Navigation.
    - [x] Move `ContactsNavigation.kt` to `feature:messaging/androidMain`.
    - [x] Move `ChannelsNavigation.kt` to `feature:settings/androidMain` or `feature:node`.
    - [x] Move `FirmwareNavigation.kt` to `feature:firmware/androidMain`.
    - [x] Move `MapNavigation.kt` to `feature:map/androidMain`.
    - [x] Fix package declarations and broken imports.
- [~] Task: Conductor - User Manual Verification 'Phase 2: Feature Module Extraction' (Protocol in workflow.md)

## Phase 3: Root Assembly & Testing
- [ ] Task: Refactor Root App Graph.
    - [ ] Update root composition to import the newly relocated navigation extension functions.
    - [ ] Remove any leftover navigation wiring from the `app` module.
- [ ] Task: Implement Navigation Assembly Tests.
    - [ ] Add basic Android instrumented or Roboelectric tests in `:app` to verify that the `NavHost` successfully constructs all feature graphs without crashing.
- [ ] Task: Review previous steps and update project documentation.
    - [ ] Update `conductor/tech-stack.md` and `conductor/product.md` if necessary to reflect the thinned app module and JetBrains Navigation 3 common usage.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Root Assembly & Testing' (Protocol in workflow.md)