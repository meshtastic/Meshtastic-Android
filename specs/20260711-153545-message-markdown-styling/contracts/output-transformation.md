# Contract: Live in-field styling (OutputTransformation)

**Location**: `feature/messaging/src/commonMain/kotlin/org/meshtastic/feature/messaging/Message.kt`
**Change**: add a markdown `OutputTransformation` and compose it with the existing `mentionOutputTransformation` on the compose field's `TextFieldState`.
**API**: Compose Multiplatform 1.11.1 `androidx.compose.foundation.text.input.OutputTransformation` / `TextFieldBuffer.addStyle`.

## Behavioral contract

| ID | Given | Then |
|----|-------|------|
| O-1 | draft `see **this**` | field displays "this" bold live while typing (FR-018) |
| O-2 | draft with a mention + markdown | both the `@Name` substitution and markdown styling render together, no offset conflict (research R5 risk) |
| O-3 | any styling applied | `TextFieldState.text` (the stored/raw value) is UNCHANGED — bytes sent are identical (FR-018 / NFR-001) |
| O-4 | draft with no markdown | field renders plain (no spurious styling) |
| O-5 | send / byte-truncation / quick-chat programmatic edits | no crash from stale selection/offset (guard as the existing code already does when clearing text) |

## Invariants

- Transformation is display-only: it MUST NOT mutate `TextFieldState.text`. Styling is applied via `TextFieldBuffer.addStyle` on the displayed buffer only.
- Must compose with (not replace) the mention transformation — either a single combined transformation or a well-defined chain; the combined result must keep offsets valid.
- Works on both Android and desktop (shared `commonMain`).

## Verification note

Because `OutputTransformation` composition is the one novel integration risk (research R5/R6 open risk), it MUST be exercised live per quickstart — not only via tests — driving: type markdown, type a mention, mix both, send, quick-chat append, and byte-limit truncation, watching for correct styling and no offset crash.
