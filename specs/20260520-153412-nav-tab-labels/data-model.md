# Data Model: Nav Tab Labels Rename

**Feature**: Reorder Bottom Navigation Tab Labels  
**Date**: 2026-05-20

## Entities

### TopLevelDestination (Enum)

The shared enum defining the canonical set of top-level navigation destinations.

| Entry | Label Resource | Route | Position |
|-------|---------------|-------|----------|
| `Messages` | `Res.string.messages` | `ContactsRoute.Contacts` | 1 |
| `Nodes` | `Res.string.nodes` | `NodesRoute.Nodes` | 2 |
| `Map` | `Res.string.map` | `MapRoute.Map()` | 3 |
| `Settings` | `Res.string.bottom_nav_settings` | `SettingsRoute.Settings()` | 4 |
| `Connect` | `Res.string.connect` | `ConnectionsRoute.Connections` | 5 |

**Changes from current**:
- `Conversations` → `Messages` (entry rename, label resource changes)
- `Connections` → `Connect` (entry rename, label resource changes)

### String Resources (New Keys)

| Key | Value (English) | Usage |
|-----|----------------|-------|
| `messages` | `Messages` | Tab label for Messages destination |
| `connect` | `Connect` | Tab label for Connect destination |

### String Resources (Retained — No Changes)

| Key | Value (English) | Usage |
|-----|----------------|-------|
| `conversations` | `Conversations` | Screen title in Contacts.kt |
| `connections` | `Connection` | Screen title in ConnectionsScreen.kt |

## Relationships

```
TopLevelDestination.Messages
  ├── label → Res.string.messages (NEW)
  ├── route → ContactsRoute.Contacts (UNCHANGED)
  └── icon  → Res.drawable.ic_forum (UNCHANGED, via TopLevelDestinationExt)

TopLevelDestination.Connect
  ├── label → Res.string.connect (NEW)
  ├── route → ConnectionsRoute.Connections (UNCHANGED)
  └── icon  → Res.drawable.ic_wifi (UNCHANGED, via TopLevelDestinationExt)
```

## State Transitions

N/A — No state machines affected. The enum is purely declarative.

## Validation Rules

- Tab order MUST remain: Messages (0), Nodes (1), Map (2), Settings (3), Connect (4)
- Enum ordinal positions MUST NOT change (preserves `MultiBackstack` ordinal-based fallback)
- Route objects MUST NOT change (preserves navigation, deep links, state restoration)
