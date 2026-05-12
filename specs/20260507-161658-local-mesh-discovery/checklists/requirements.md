# Requirements Quality Checklist — Local Mesh Discovery

Use this checklist to review the discovery specification before implementation starts.

## Scope and User Value

- [ ] The spec clearly states the user problem Local Mesh Discovery solves.
- [ ] The feature scope is limited to diagnostic discovery, summary, history, and export.
- [ ] Out-of-scope items are explicitly listed.
- [ ] The feature remains useful even when AI is unavailable.

## User Stories

- [ ] Five user stories are present and remain prioritized P1-P5.
- [ ] Each user story is independently testable.
- [ ] Each user story explains why the priority matters.
- [ ] Each user story includes at least one independent test.
- [ ] Acceptance scenarios cover success, cancellation, and failure cases where relevant.

## Meshtastic-Android Architecture Fit

- [ ] The spec uses Compose Multiplatform + Material 3 terminology.
- [ ] The spec uses Navigation 3 typed routes / `NavKey` patterns.
- [ ] The spec keeps business logic in `commonMain`.
- [ ] Platform-specific work is limited to Android/Desktop map, export, and AI integrations.
- [ ] The feature module location is `feature/discovery/` and follows the `meshtastic.kmp.feature` convention.
- [ ] Koin module and ViewModel wiring expectations are documented.

## Persistence and Data Modeling

- [ ] Persistence uses Room KMP and aligns with existing `core:database` conventions.
- [ ] Session, preset-result, and discovered-node entities are all defined.
- [ ] Entity relationships, cascade behavior, and indexing are documented.
- [ ] DAO responsibilities are documented.
- [ ] Migration strategy is called out for `MeshtasticDatabase`.
- [ ] The design avoids storing raw packet dumps when aggregate/session reconstruction data is sufficient.

## Integration Points

- [ ] Neighbor info integration uses the existing `NeighborInfoHandler` path.
- [ ] Preset mutation reuses the existing admin/config flow.
- [ ] BLE reconnect handling reuses `BleReconnectPolicy` / `BleRadioTransport` behavior.
- [ ] Map rendering reuses the existing CompositionLocal provider pattern.
- [ ] Preferences use DataStore via `core:prefs`.
- [ ] 2.4 GHz gating uses existing hardware metadata infrastructure.

## UX and Product Behavior

- [ ] The scan state machine is documented.
- [ ] Progress, reconnect wait, analysis, and cancellation states are all defined.
- [ ] Partial sessions remain visible in history and summary.
- [ ] Summary ranking logic is documented even without AI.
- [ ] Map fallback behavior is defined for unsupported targets.
- [ ] Export/share behavior is defined for Android and Desktop.

## Safety and Failure Handling

- [ ] The feature restores the user’s home preset after completion, stop, or failure.
- [ ] The spec defines what happens when reconnect never succeeds.
- [ ] The spec defines what happens when hardware capability is unknown.
- [ ] The spec defines what happens when AI is unavailable or errors.
- [ ] The spec defines what happens when map rendering or export snapshots fail.
- [ ] The spec never assumes unsupported 2.4 GHz hardware can run a scan.

## Resource and Style Conventions

- [ ] User-visible strings are planned for `core/resources/src/commonMain/composeResources/values/strings.xml`.
- [ ] Icon guidance references `MeshtasticIcons`.
- [ ] Formatting guidance references shared Meshtastic formatters / models.
- [ ] All framework references are specific to the Meshtastic-Android KMP stack.

## Testing and Delivery

- [ ] The plan includes both targeted module tests and repository-wide verification commands.
- [ ] The tasks are phased and dependency-ordered.
- [ ] Parallelization opportunities are called out.
- [ ] The quickstart guide includes `test` and `allTests` guidance.
- [ ] The deep-link contract is documented separately.

## Review Outcome

- [ ] Ready for implementation
- [ ] Needs clarification before implementation
- [ ] Needs scope reduction before implementation
