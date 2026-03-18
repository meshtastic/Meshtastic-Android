# Implementation Plan: Extract Android Navigation

## Phase 1: Preparation & Base Module Abstraction [checkpoint: 421a587]
- [x] Task: Review current navigation graph assembly in `app/src/main/kotlin/org/meshtastic/app/navigation/`.
    - [x] Identify dependencies between feature navigation graphs and core routing definitions.
    - [x] Create missing directory structures in feature modules' `androidMain/kotlin/org/meshtastic/feature/*/navigation` if they don't exist.
- [x] Task: Conductor - User Manual Verification 'Phase 1: Preparation & Base Module Abstraction' (Protocol in workflow.md)

## Phase 2: Feature Module Extraction
- [ ] Task: Extract Settings Navigation.
    - [ ] Move `SettingsNavigation.kt` to `feature:settings/androidMain`.
    - [ ] Fix package declarations and broken imports.
- [ ] Task: Extract Nodes & Connections Navigation.
    - [ ] Move `NodesNavigation.kt` to `feature:node/androidMain`.
    - [ ] Move `ConnectionsNavigation.kt` to `feature:connections/androidMain`.
    - [ ] Fix package declarations and broken imports.
- [ ] Task: Extract Messaging & Remaining Navigation.
    - [ ] Move `ContactsNavigation.kt` to `feature:messaging/androidMain`.
    - [ ] Move `ChannelsNavigation.kt` to `feature:settings/androidMain` or `feature:node`.
    - [ ] Move `FirmwareNavigation.kt` to `feature:firmware/androidMain`.
    - [ ] Move `MapNavigation.kt` to `feature:map/androidMain`.
    - [ ] Fix package declarations and broken imports.
- [ ] Task: Conductor - User Manual Verification 'Phase 2: Feature Module Extraction' (Protocol in workflow.md)

## Phase 3: Root Assembly & Testing
- [ ] Task: Refactor Root App Graph.
    - [ ] Update root composition to import the newly relocated navigation extension functions.
    - [ ] Remove any leftover navigation wiring from the `app` module.
- [ ] Task: Implement Navigation Assembly Tests.
    - [ ] Add basic Android instrumented or Roboelectric tests in `:app` to verify that the `NavHost` successfully constructs all feature graphs without crashing.
- [ ] Task: Review previous steps and update project documentation.
    - [ ] Update `conductor/tech-stack.md` and `conductor/product.md` if necessary to reflect the thinned app module and JetBrains Navigation 3 common usage.
- [ ] Task: Conductor - User Manual Verification 'Phase 3: Root Assembly & Testing' (Protocol in workflow.md)