# Feature Specification: [FEATURE NAME]

**Feature Branch**: `[###-feature-name]`
**Created**: [DATE]
**Status**: Draft
**Input**: User description: "$ARGUMENTS"
**Cross-Platform Spec**: <!-- Link to meshtastic/design/features/ spec, or "N/A — platform-specific only" with justification -->

## Summary

<!--
  Provide a brief (2-3 sentence) summary of the feature, its purpose, and what
  user problem it solves. Mention which modules are primarily affected.

  CROSS-PLATFORM CHECK: Before writing this spec, check meshtastic/design/features/
  for an existing cross-platform behavior spec. If one exists, this spec should describe
  the Android-specific scope and acceptance criteria — not redefine the cross-platform
  behavior. If none exists and this feature affects multiple platforms, create one first
  using the TEMPLATE.md in that repo.
-->

## Goals

<!--
  List 3-5 goals this feature achieves. Be specific and measurable.
-->

## Non-Goals

<!--
  Explicitly state what this feature does NOT do to prevent scope creep.
-->

## User Scenarios & Testing *(mandatory)*

<!--
  IMPORTANT: User stories should be PRIORITIZED as user journeys ordered by importance.
  Each user story/journey must be INDEPENDENTLY TESTABLE - meaning if you implement just ONE of them,
  you should still have a viable MVP (Minimum Viable Product) that delivers value.

  Assign priorities (P1, P2, P3, etc.) to each story, where P1 is the most critical.
  Think of each story as a standalone slice of functionality that can be:
  - Developed independently
  - Tested independently
  - Deployed independently
  - Demonstrated to users independently
-->

### User Story 1 - [Brief Title] (Priority: P1)

[Describe this user journey in plain language]

**Why this priority**: [Explain the value and why it has this priority level]

**Independent Test**: [Describe how this can be tested independently - e.g., "Can be fully tested by [specific action] and delivers [specific value]"]

**Acceptance Scenarios**:

1. **Given** [initial state], **When** [action], **Then** [expected outcome]
2. **Given** [initial state], **When** [action], **Then** [expected outcome]

---

### User Story 2 - [Brief Title] (Priority: P2)

[Describe this user journey in plain language]

**Why this priority**: [Explain the value and why it has this priority level]

**Independent Test**: [Describe how this can be tested independently]

**Acceptance Scenarios**:

1. **Given** [initial state], **When** [action], **Then** [expected outcome]

---

### User Story 3 - [Brief Title] (Priority: P3)

[Describe this user journey in plain language]

**Why this priority**: [Explain the value and why it has this priority level]

**Independent Test**: [Describe how this can be tested independently]

**Acceptance Scenarios**:

1. **Given** [initial state], **When** [action], **Then** [expected outcome]

---

[Add more user stories as needed, each with an assigned priority]

### Edge Cases

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right edge cases.
-->

- What happens when [boundary condition]?
- How does system handle [error scenario]?

## Requirements *(mandatory)*

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right functional requirements.
-->

### Functional Requirements

- **FR-001**: System MUST [specific capability]
- **FR-002**: System MUST [specific capability]

### Non-Functional Requirements

- **NFR-001**: [Performance, accessibility, or quality requirement]

## Architecture

<!--
  ACTION REQUIRED: Describe the layout structure, data flow, and key components.
  Include ASCII diagrams for visual layouts and Mermaid flowcharts for data flow.
  Reference existing composables from core:ui where applicable.
-->

### Key Components

<!--
  List the components involved in this feature with their module paths and purpose.
  Reference existing components from core:ui, core:model, etc. where applicable.
-->

| Component | Module / File | Purpose |
|-----------|---------------|---------|
| [Component] | `feature/[name]/component/` | [Purpose] |
| [Existing Component] | `core/ui/component/` | [Reuse purpose] |

## Source-Set Impact

<!--
  ACTION REQUIRED: Identify which KMP source sets this feature affects.
  All business logic and UI MUST be in commonMain (Constitution I, III).
-->

| Source Set | Impact | Justification |
|-----------|--------|---------------|
| `commonMain` | [New files / Modified files] | All business logic and UI |
| `androidMain` | [None / Platform integration only] | [Justification if needed] |
| `jvmMain` | [None / Shared JVM code] | [Justification if needed] |

## Design Standards Compliance

<!--
  ACTION REQUIRED: Note any UI elements that must be reviewed against the
  Meshtastic Client Design Standards (Constitution V). Flag intentional
  deviations with rationale.
-->

- [ ] New screens reviewed against [design standards](https://raw.githubusercontent.com/meshtastic/design/refs/heads/master/standards/meshtastic_design_standards_latest.md)
- [ ] M3 component selection verified (e.g., `SwitchPreference` not raw `Switch`)
- [ ] Accessibility: TalkBack semantics, touch targets, color-independent info
- [ ] Typography: `titleMediumEmphasized` for emphasis, M3 scale for hierarchy

## Privacy Assessment

<!--
  ACTION REQUIRED: Confirm this feature does not violate Constitution IV.
  If the feature handles any sensitive data, document the safeguards.
-->

- [ ] No PII, location data, or cryptographic keys logged or exposed
- [ ] No new network calls that transmit user data
- [ ] Proto submodule (`core/proto`) not modified (read-only upstream)

## Success Criteria *(mandatory)*

<!--
  ACTION REQUIRED: Define measurable success criteria.
  These must be technology-agnostic and measurable.
-->

### Measurable Outcomes

- **SC-001**: [Measurable metric]
- **SC-002**: [Measurable metric]

## Assumptions

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right assumptions based on reasonable defaults
  chosen when the feature description did not specify certain details.
-->

- All business logic and UI composables reside in `commonMain` source set
- String resources added to `core/resources/src/commonMain/composeResources/values/strings.xml`
- Icons use `MeshtasticIcons` (from `core/ui/icon/`)
- Float values pre-formatted with `NumberFormatter.format()` (CMP constraint)
- [Additional feature-specific assumptions]
