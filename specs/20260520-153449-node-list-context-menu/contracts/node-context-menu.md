# UI Contract: NodeContextMenu

## Component: `NodeContextMenu`

**Location**: `feature/node/src/commonMain/kotlin/org/meshtastic/feature/node/component/NodeContextMenu.kt`

### Interface

```kotlin
@Composable
fun NodeContextMenu(
    expanded: Boolean,
    node: Node,
    onFavorite: () -> Unit,
    onMute: () -> Unit,
    onMessage: () -> Unit,
    onTraceRoute: () -> Unit,
    onIgnore: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
)
```

### Rendered Structure (Canonical Order)

```
┌─────────────────────────────────────┐
│ DropdownMenuGroup (shapes)          │
│ ├─ ⭐ Add to favorites             │  ← position 1 (disabled if ignored)
│ ├─ 🔇 Mute notifications           │  ← position 2 (hidden if !canMuteNode)
│ ├─ 💬 Message                       │  ← position 3 (disabled if ignored)
│ └─ 🔀 Trace Route                   │  ← position 4 (disabled if ignored)
├─────────────────────────────────────┤
│ DropdownMenuGroup (shapes)          │
│ ├─ 🚫 Ignore                        │  ← position 5 (red, always enabled)
│ └─ 🗑️ Remove                        │  ← position 6 (red, disabled if ignored)
└─────────────────────────────────────┘
```

### Behavioral Contract

| Scenario | Input State | Expected Behavior |
|----------|-------------|-------------------|
| Normal node | `isIgnored=false, canMuteNode=true` | All 6 items visible, all enabled |
| Ignored node | `isIgnored=true` | Favorite, Message, Trace Route, Remove disabled; Ignore shows "Remove from ignored" |
| Cannot mute | `canMuteNode=false` | Mute item hidden; remaining 5 items in canonical positions |
| Already favorite | `isFavorite=true` | Shows "Remove from favorites" with filled star icon |
| Already muted | `isMuted=true` | Shows "Unmute" with volume-off icon |
| Local node (self) | `node.num == ourNode.num` | Menu NEVER shown (suppressed at call site) |

### Callback Effects

| Callback | Action |
|----------|--------|
| `onFavorite` | Toggles node favorite status via `NodeManagementActions.requestFavoriteNode()` |
| `onMute` | Toggles node mute status via `NodeManagementActions.requestMuteNode()` |
| `onMessage` | Navigates to direct message conversation via `navigateToMessages(contactKey)` |
| `onTraceRoute` | Initiates traceroute request via `NodeRequestActions.requestTraceroute()` |
| `onIgnore` | Toggles node ignore status via `NodeManagementActions.requestIgnoreNode()` |
| `onRemove` | Removes node from mesh database via `NodeManagementActions.requestRemoveNode()` |

### Accessibility

- Each `DropdownMenuItem` gets automatic TalkBack semantics from M3 component
- `leadingIcon` has `contentDescription = null` (icon is decorative; text label is sufficient)
- Touch targets meet M3 minimum 48dp requirement (handled by `DropdownMenuItem`)

### Icons

| Item | Icon | Condition |
|------|------|-----------|
| Favorite | `MeshtasticIcons.Favorite` / `MeshtasticIcons.NotFavorite` | Based on `isFavorite` |
| Mute | `MeshtasticIcons.VolumeUp` / `MeshtasticIcons.VolumeOff` | Based on `isMuted` |
| Message | `MeshtasticIcons.Message` | Always |
| Trace Route | `MeshtasticIcons.Route` | Always |
| Ignore | `MeshtasticIcons.DoDisturb` | Always (red tint) |
| Remove | `MeshtasticIcons.DeleteNode` | Always (red tint) |

### String Resources

| Resource Key | Value | Used When |
|-------------|-------|-----------|
| `add_favorite` | "Add to favorites" | `!isFavorite` |
| `remove_favorite` | "Remove from favorites" | `isFavorite` |
| `mute_notifications` | "Mute notifications" | `!isMuted` |
| `unmute` | "Unmute" | `isMuted` |
| `message` | "Message" | Always |
| `trace_route` | "Trace Route" | Always (NEW) |
| `ignore` | "Ignore" | `!isIgnored` |
| `remove_ignored` | "Remove from ignored" | `isIgnored` |
| `remove` | "Remove" | Always |
