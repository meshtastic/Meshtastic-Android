# Data Model: Remove Admin Channel Enabled Toggle

## Summary

This feature does not introduce, modify, or remove any data model entities. It is a pure UI deletion.

## Entities

### Config.SecurityConfig (unchanged)

The proto-generated `Config.SecurityConfig` data class retains the `admin_channel_enabled: Boolean` field. No schema changes.

| Field | Type | Change |
|-------|------|--------|
| `admin_channel_enabled` | `Boolean` | **No change** — remains in proto, no longer surfaced in UI |
| `is_managed` | `Boolean` | No change |
| `admin_key` | `List<ByteString>` | No change |
| ... | ... | No change |

## Relationships

No new relationships introduced. The existing relationship between `SecurityConfigScreen` composable and `Config.SecurityConfig` proto model remains — the screen simply renders one fewer field from the model.

## State Transitions

N/A — No state machine changes. The form state (`formState`) continues to hold the full `SecurityConfig` including `admin_channel_enabled`; it is simply not bound to any UI element after this change.
