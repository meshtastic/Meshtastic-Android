# Data Model: Node List Context Menu Alignment

## Entities

### NodeContextMenu (UI Component State)

No new persistent data model entities are introduced by this feature. The context menu operates on the existing `Node` model and uses existing capabilities/state.

| Field | Type | Source | Purpose |
|-------|------|--------|---------|
| `node.isFavorite` | Boolean | `Node` model | Toggle favorite label |
| `node.isMuted` | Boolean | `Node` model | Toggle mute label |
| `node.isIgnored` | Boolean | `Node` model | Disable Message/TraceRoute/Favorite |
| `node.capabilities.canMuteNode` | Boolean | `Node.Capabilities` | Conditionally show mute item |
| `node.num` | Int | `Node` model | Target for trace route + message routing |
| `node.user.long_name` | String | `Node.User` | Display name for trace route logging |

### Menu Item Order (Canonical)

| Position | Item | Condition | Enabled |
|----------|------|-----------|---------|
| 1 | Add to favorites / Remove from favorites | Always shown | `!node.isIgnored` |
| 2 | Mute notifications / Unmute | `canMuteNode == true` | Always |
| 3 | Message | Always shown | `!node.isIgnored` |
| 4 | Trace Route | Always shown | `!node.isIgnored` |
| 5 | Ignore / Remove from ignored | Always shown | Always |
| 6 | Remove | Always shown | `!node.isIgnored` |

### Callback Interface Changes

```kotlin
// NodeContextMenu composable parameters (additions in bold)
fun NodeContextMenu(
    expanded: Boolean,
    node: Node,
    onFavorite: () -> Unit,
    onMute: () -> Unit,
    onMessage: () -> Unit,      // NEW
    onTraceRoute: () -> Unit,   // NEW
    onIgnore: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
)
```

## State Transitions

No state machine changes. Existing toggle behaviors (favorite, mute, ignore) are unchanged. New actions (Message, Trace Route) are fire-and-forget — they trigger navigation or a network request respectively.

## Validation Rules

- Context menu MUST NOT appear for local node (`node.num == ourNode.num`) — enforced at call site
- Message and Trace Route MUST be disabled when `node.isIgnored == true`
- Mute item MUST be hidden (not disabled) when `node.capabilities.canMuteNode == false`
- Menu MUST display items in exact canonical order regardless of node state
