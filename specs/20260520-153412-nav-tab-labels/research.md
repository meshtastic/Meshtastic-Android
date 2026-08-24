# Research: Nav Tab Labels Rename

**Feature**: Reorder Bottom Navigation Tab Labels  
**Date**: 2026-05-20

## Research Tasks

### 1. Enum Rename Impact on Serialization / State Restoration

**Decision**: Renaming `TopLevelDestination.Conversations` → `Messages` and `TopLevelDestination.Connections` → `Connect` is safe for navigation state.

**Rationale**: 
- Navigation state is keyed by `NavKey` route objects (e.g., `ContactsRoute.Contacts`, `ConnectionsRoute.Connections`), NOT by enum entry names.
- The `TopLevelDestination.route` property maps to typed route objects — the enum name is never serialized.
- `MultiBackstack` stores `NavKey` instances in its back stack maps, keyed by route class identity (`it::class == dest.route::class`).
- Process death restoration uses `SavedStateHandle` with route objects, not enum string names.

**Alternatives Considered**:
- Keep old enum names and only change labels: Rejected because the spec explicitly requires renaming entries for code clarity and alignment with the canonical naming.

### 2. String Resource Key Strategy

**Decision**: Add new keys `messages` and `connect` for tab labels. Retain existing keys `conversations` and `connections` for screen titles.

**Rationale**:
- `conversations` is used in `Contacts.kt` for the screen title (confirmed from spec clarification session).
- `connections` is used in `ConnectionsScreen.kt` for the screen/section title.
- Separating tab label keys from screen title keys allows independent localization and prevents unintended label changes elsewhere.
- The `connections` key currently has value "Connection" (singular) — this is a screen title, not a tab label.

**Alternatives Considered**:
- Reuse existing keys and rename their values: Rejected because it would change screen titles in feature modules that are out of scope.
- Use `tab_messages` / `tab_connect` prefixed keys: Rejected in favor of simpler `messages` / `connect` per clarification decision.

### 3. Compose Resource Import Changes

**Decision**: After adding `messages` and `connect` to `strings.xml`, the generated `Res.string.messages` and `Res.string.connect` accessors will be available after a build.

**Rationale**:
- Compose Multiplatform generates accessor objects from resource keys at compile time.
- Import statements in `TopLevelDestination.kt` will change from `org.meshtastic.core.resources.conversations` / `org.meshtastic.core.resources.connections` to `org.meshtastic.core.resources.messages` / `org.meshtastic.core.resources.connect`.
- Old imports (`conversations`, `connections`) remain valid since those keys are retained — they just won't be used in `TopLevelDestination.kt` anymore.

**Alternatives Considered**: None — this is the standard Compose Resources workflow.

### 4. Test Impact Assessment

**Decision**: Update `MultiBackstackTest.kt` references from `TopLevelDestination.Connections` to `TopLevelDestination.Connect`. The `DesktopTopLevelDestinationParityTest.kt` tests enum parity generically (iterates `entries`) and requires no changes.

**Rationale**:
- `MultiBackstackTest.kt` explicitly references `TopLevelDestination.Connections` in 8 locations for default tab setup.
- The parity test uses `TopLevelDestination.entries` enumeration, so it adapts automatically to renamed entries.
- No test logic changes needed — only identifier renames.

**Alternatives Considered**: None — mechanical rename with no behavioral changes.

### 5. Sort Script for String Resources

**Decision**: Run `python3 scripts/sort-strings.py` after adding new keys to maintain alphabetical ordering.

**Rationale**:
- Constitution (Development Workflow) mandates running this script after any string resource addition.
- Keys `connect` and `messages` will be inserted alphabetically by the script.
- `strings-index.txt` will be regenerated automatically.

**Alternatives Considered**: None — this is a mandatory workflow step.

## Summary of Resolved Items

| Item | Resolution |
|------|-----------|
| Enum rename breaks state? | No — routes use typed objects, not enum names |
| String key strategy | New keys `messages`/`connect`; old keys retained |
| Import changes | Mechanical — generated accessors update on build |
| Test updates needed | `MultiBackstackTest.kt` identifier renames only |
| Post-change workflow | `python3 scripts/sort-strings.py` required |

All NEEDS CLARIFICATION items resolved. No open questions remain.
