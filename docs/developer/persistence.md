---
title: Persistence
nav_order: 6
last_updated: 2026-05-12
aliases:
  - room
  - database
  - datastore
  - prefs
---

# Persistence

How the Meshtastic app stores data across different mechanisms.

## Room KMP Database

**Module:** `core:database`

The primary structured data store:
- Node information and history
- Message history
- Waypoints
- Telemetry data
- Channel configurations

### Key Points

- Uses Room KMP for cross-platform compatibility
- Migrations managed through Room's built-in migration system
- DAO interfaces live in `core:database`
- Repository layer in `core:repository` provides the public API

### What's Stored in Room

| Entity | Description |
|--------|-------------|
| Nodes | All known mesh nodes and metadata |
| Messages | Message history (channel and direct) |
| Waypoints | Shared geographic points |
| Telemetry | Device, environment, power metrics |
| Channels | Channel configurations |

## DataStore Preferences

**Module:** `core:datastore`

For lightweight key-value preferences:
- Connection state
- Last connected device
- UI preferences

## Core Prefs

**Module:** `core:prefs`

Higher-level preferences abstraction:
- User-facing settings
- App behavior configuration
- Feature toggles

## What Docs Intentionally Skip

The `feature:docs` module does **not** use Room or any persistent database:
- Documentation content is packaged as build-time assets
- The docs corpus is versioned with the app binary
- No migration story is needed for docs content
- Optional UX state (last viewed page) could use `core:prefs` but is not part of the docs data model

This is an intentional design decision to keep documentation:
- Fully offline without database overhead
- Replaceable with each app update
- Simple to validate and test

## Best Practices

- Use Room for structured, queryable data that changes at runtime
- Use DataStore for simple preferences and state
- Use bundled resources/assets for static content
- Never store sensitive data (keys, passwords) in plain Room tables
- Always provide migrations for schema changes

---

