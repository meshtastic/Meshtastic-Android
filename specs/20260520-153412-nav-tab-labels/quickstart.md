# Quickstart: Nav Tab Labels Rename

**Feature**: Reorder Bottom Navigation Tab Labels  
**Date**: 2026-05-20

## Overview

This feature renames two `TopLevelDestination` enum entries and their associated string resources to align with the Meshtastic cross-platform Menu Alignment Audit.

## Implementation Steps (High-Level)

### Step 1: Add String Resources

Add two new keys to `core/resources/src/commonMain/composeResources/values/strings.xml`:
```xml
<string name="connect">Connect</string>
<string name="messages">Messages</string>
```

Then run: `python3 scripts/sort-strings.py`

### Step 2: Rename Enum Entries

In `core/navigation/src/commonMain/kotlin/org/meshtastic/core/navigation/TopLevelDestination.kt`:
- `Conversations(Res.string.conversations, ...)` → `Messages(Res.string.messages, ...)`
- `Connections(Res.string.connections, ...)` → `Connect(Res.string.connect, ...)`

Update imports accordingly.

### Step 3: Update All References

Files requiring mechanical rename of `TopLevelDestination.Conversations` → `.Messages` and `.Connections` → `.Connect`:

1. `core/ui/.../TopLevelDestinationExt.kt` — icon `when` branches
2. `core/ui/.../MeshtasticNavigationSuite.kt` — any explicit references
3. `core/navigation/.../MultiBackstack.kt` — default tab reference
4. `core/navigation/src/commonTest/.../MultiBackstackTest.kt` — test setup
5. `androidApp/.../Main.kt` — Android entry point
6. `desktopApp/.../Main.kt` — Desktop entry point
7. `desktopApp/.../DesktopNavigation.kt` — Desktop nav shell

### Step 4: Verify

```bash
./gradlew spotlessApply spotlessCheck detekt assembleDebug test allTests
```

## Key Decisions

| Decision | Rationale |
|----------|-----------|
| New string keys (not reusing old) | Old keys used as screen titles elsewhere |
| Enum entry rename (not just label) | Code clarity + spec requirement |
| No route changes | Preserves deep links & state restoration |
| No localization changes | Deferred to Crowdin sync cycle |

## Risks & Mitigations

| Risk | Mitigation |
|------|-----------|
| Missed reference causes compile error | IDE rename refactoring + full `allTests` build |
| Ordinal shift breaks MultiBackstack | Entries stay in same position — order unchanged |
| Localized users see English fallback | Expected behavior for new keys until Crowdin sync |
