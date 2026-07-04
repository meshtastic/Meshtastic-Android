# Quickstart: Node List Context Menu Alignment

## What This Feature Does

Aligns the node list long-press context menu to the canonical cross-platform order and adds two new quick-access actions (Message, Trace Route).

## Key Files to Modify

| File | Change |
|------|--------|
| `feature/node/src/commonMain/.../component/NodeContextMenu.kt` | Reorder items, add MessageMenuItem + TraceRouteMenuItem, change mute label |
| `feature/node/src/commonMain/.../list/NodeListScreen.kt` | Wire `onMessage` and `onTraceRoute` callbacks to NodeContextMenu |
| `feature/node/src/commonMain/.../list/NodeListViewModel.kt` | Add `traceRouteNode(node)` and `getDirectMessageRoute(node)` methods |
| `core/resources/src/commonMain/composeResources/values/strings.xml` | Add `trace_route` string resource |

## Implementation Steps (High Level)

1. **Add string resource**: Add `<string name="trace_route">Trace Route</string>` to `strings.xml`
2. **Run sort-strings**: `python3 scripts/sort-strings.py`
3. **Add ViewModel methods**: Inject `NodeRequestActions` into `NodeListViewModel`, add `traceRouteNode()` and `getDirectMessageRoute()` methods
4. **Add new menu items**: Create `MessageMenuItem` and `TraceRouteMenuItem` composables in `NodeContextMenu.kt`
5. **Reorder menu**: Arrange items as Favorite → Mute → Message → TraceRoute | Ignore → Remove
6. **Change mute label**: Replace `Res.string.mute_always` with `Res.string.mute_notifications`
7. **Update NodeContextMenu signature**: Add `onMessage` and `onTraceRoute` parameters
8. **Wire in NodeListScreen**: Pass new callbacks from ViewModel to NodeContextMenu
9. **Verify**: Run `./gradlew spotlessApply spotlessCheck detekt :feature:node:allTests`

## Verification Commands

```bash
# Full verification for touched modules
./gradlew spotlessApply spotlessCheck detekt :feature:node:allTests :core:resources:allTests

# Build check (all platforms)
./gradlew assembleDebug kmpSmokeCompile

# After push
gh pr checks <PR>
```

## Important Notes

- The local node (self) context menu suppression is **already implemented** — no changes needed
- `Res.string.message` ("Message") and `Res.string.mute_notifications` ("Mute notifications") **already exist**
- `MeshtasticIcons.Message` and `MeshtasticIcons.Route` **already exist**
- `NodeMenuAction.DirectMessage` and `NodeMenuAction.TraceRoute` **already exist** in the sealed class
- `NodeRequestActions.requestTraceroute()` **already exists** — just needs to be injected into `NodeListViewModel`
