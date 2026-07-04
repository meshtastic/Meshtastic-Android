# Research: Node List Context Menu Alignment

## Research Questions & Findings

### RQ-1: How does the existing context menu composable work?

**Decision**: Extend `NodeContextMenu.kt` with two new `DropdownMenuItem` composables and reorder existing items.

**Rationale**: The current `NodeContextMenu` composable uses a `DropdownMenu` with `DropdownMenuGroup` containers. It accepts individual callbacks per action. This pattern is clean and extensible — adding `onMessage` and `onTraceRoute` callbacks maintains the same architecture.

**Alternatives considered**:
- Creating a sealed interface for menu actions and a single callback → Rejected because it would break the existing API contract used by `NodeListScreen` and introduce unnecessary refactoring.
- Using a `LazyColumn` instead of `DropdownMenu` → Rejected; `DropdownMenu` is the M3 standard for context menus and already in use.

### RQ-2: How to wire "Message" (Direct Message) from the node list?

**Decision**: Reuse existing `getDirectMessageRoute()` from `NodeDetailViewModel` by extracting the logic into a shared utility or by adding the same capability to `NodeListViewModel`.

**Rationale**: `NodeDetailViewModel.getDirectMessageRoute(node, ourNode)` already computes the correct conversation key. The `NodeListViewModel` already has access to `ourNode` via its state flow. Adding a `getDirectMessageRoute(node)` method to `NodeListViewModel` that delegates to the same repository logic keeps the change minimal.

**Alternatives considered**:
- Navigating to node detail first, then to messages → Rejected; defeats the purpose of a quick-access shortcut.
- Copying the route computation inline → Rejected; DRY violation. Extract to a shared helper in the `detail` package.

### RQ-3: How to wire "Trace Route" from the node list?

**Decision**: Add `NodeRequestActions` dependency to `NodeListViewModel` (or reuse the existing `NodeManagementActions` pattern) and call `requestTraceroute()`.

**Rationale**: `NodeRequestActions.requestTraceroute(scope, destNum, longName)` is already defined and used by `NodeDetailViewModel` and `MetricsViewModel`. The `NodeListViewModel` can inject the same `NodeRequestActions` interface via Koin.

**Alternatives considered**:
- Navigating to the traceroute metrics screen → Rejected; spec requires initiating trace route directly from the menu without navigation.
- Creating a new use case class → Over-engineering for a single function call.

### RQ-4: What string resources already exist vs. need to be added?

**Decision**: Use existing `Res.string.message` ("Message") and `Res.string.mute_notifications` ("Mute notifications"). Add new `trace_route` string ("Trace Route"). Change the mute menu item to reference `Res.string.mute_notifications` instead of `Res.string.mute_always`.

**Rationale**:
- `message` = "Message" → Already exists (line 730 in strings.xml)
- `mute_notifications` = "Mute notifications" → Already exists (line 780)
- `traceroute` = "Traceroute" → Exists but spec says "Trace Route" (two words). Add `trace_route` = "Trace Route" for the menu item label to match canonical spec.

**Alternatives considered**:
- Reusing `traceroute` string → Rejected because it reads "Traceroute" (one word) but the canonical menu order specifies "Trace Route" (two words, title case).

### RQ-5: What icons to use for Message and Trace Route?

**Decision**: Use `MeshtasticIcons.Message` for the Message action and `MeshtasticIcons.Route` for Trace Route.

**Rationale**:
- `MeshtasticIcons.Message` (`ic_message`) — standard messaging icon, used elsewhere in the app for DM-related UI.
- `MeshtasticIcons.Route` (`ic_route`) — routing/path icon from the map icon set, semantically matches traceroute.

**Alternatives considered**:
- `ChatBubbleOutline` for message → Less recognizable; `Message` is the standard.
- `MapCompass` for trace route → Semantically wrong; compass implies direction, not routing.

### RQ-6: How is the local node (self) suppression handled?

**Decision**: Already implemented. The current `NodeListScreen` sets `onLongClick = null` when `node.num == ourNode?.num`, which prevents the context menu from showing.

**Rationale**: Code at line 192-196 of `NodeListScreen.kt`:
```kotlin
val longClick = if (node.num != ourNode?.num) { { expanded = true } } else { null }
```
This satisfies FR-009 without additional changes.

**Alternatives considered**: N/A — requirement already met.

### RQ-7: How should disabled state work for ignored nodes?

**Decision**: Pass `enabled = !node.isIgnored` to the new `MessageMenuItem` and `TraceRouteMenuItem`, consistent with existing `FavoriteMenuItem` behavior.

**Rationale**: The existing `FavoriteMenuItem` already uses `enabled = !node.isIgnored`. Applying the same pattern to Message and Trace Route ensures consistency (FR-007).

**Alternatives considered**:
- Hiding items entirely for ignored nodes → Rejected; spec says "disabled" not "hidden".
- Using a different disabled visual → Not needed; M3 `DropdownMenuItem` handles disabled states automatically.
