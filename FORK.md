# FORK.md

Divergences of this fork from upstream [`meshtastic/Meshtastic-Android`](https://github.com/meshtastic/Meshtastic-Android).

Purpose of the fork: a phone-based offline map for hiking in French Guiana. A ~400 MB SCAN 25 / Plan IGN `.mbtiles`
file, no network in the field, and a non-technical second user who must be able to open the app and see the map with
no setup.

Licence: GPL-3.0, same as upstream. If an APK is distributed, the sources must be published with it.

Trademark: "Meshtastic" is a registered trademark. The app name and `applicationId` are unchanged so far, which is
fine for personal use. They must be changed before any distribution beyond that.

## Divergences

| # | Area | Change | Upstreamable |
|---|------|--------|--------------|
| 1 | Offline map persistence | Imported custom tile providers and the active layer now survive an app restart. | **Yes** — pure bug fix, no fork-specific behaviour. |
| 2 | Offline map priority | On start-up, an imported local (MBTiles) provider is selected automatically when no valid saved selection applies. | No — deliberate product choice for this fork. |
| 3 | Bluetooth-only connections | The transport selector is hidden and the Connections pane is pinned to BLE. | No — upstream deliberately supports three transports. |
| 4 | Compass shortcut on the map | A map toolbar button opens the existing compass straight onto the favourite node. | Possibly — the `openCompass` route flag is generic; the map button is opinionated. |
| 5 | Map control legibility | Map buttons pin an explicit 44dp touch target and a 26dp glyph. | **Yes** — it brings the controls up to the project's own documented minimum. |

### 4. Compass shortcut on the map

The compass itself is upstream's, and it already does the hard part: it reads the phone's magnetometer, so the arrow
points where to walk rather than showing a bearing relative to north, and it reports distance, alignment, and the
degraded cases (no magnetometer, no location permission, location off, no fix). Nothing here reimplements it.

What the fork adds is reach. Upstream's path is: node list → find the right node → open it → tap the compass. This
adds a toolbar button on the main map that lands on the compass in one tap:

- `NodesRoute.NodeDetail` gains `openCompass: Boolean = false`, and `NodeDetailScreen` opens the compass overlay once
  the node has loaded when it is set.
- `MapViewProvider.MapView` and `LocalMapMainScreenProvider` gain a `navigateToNodeCompass` callback, defaulting to
  plain node details so nothing else has to change. The osmdroid (fdroid) provider accepts it and ignores it.
- The button appears only when a favourite node has a known position, so it is never a control that does nothing.

Targeting is by **favourite node**, not a hard-coded node number: mark the companion's device as a favourite once,
and it stays changeable in the field without a rebuild. If several nodes are favourited, the first with a position
wins — fine for a two-person trip, worth revisiting for a larger group.

### 5. Map control legibility

`MapButton` sets its touch target and glyph size explicitly instead of inheriting Material's defaults, which left the
map controls below the 44dp minimum in `.skills/design-standards`. Buttons are now 44dp with a 26dp glyph.

Sized to 44dp and not the roomier 48dp on purpose: `HorizontalFloatingToolbar` does not scroll, and a fully-populated
map toolbar (compass, find-favourite, filter, map type, layers, site planner, location) at 48dp overflows a 360dp-wide
screen and clips its last buttons. 44dp meets the standard and still fits. If the toolbar ever gains horizontal
scrolling, 48dp becomes the better value.

Deliberately **not** changed: the icon glyphs themselves. Which symbols read as unclear is a judgement that needs eyes
on a real screen, and swapping artwork blind would trade a known set of icons for an unverified one.

### 1. Offline map persistence (bug fix)

Symptom: after every app close, the imported `.mbtiles` had to be re-imported and its layer re-enabled by hand.

Two independent root causes, each sufficient on its own:

- **The provider list was lost on every cold start.** `CustomTileProviderRepositoryImpl` read
  `MapTileProviderPrefs.customTileProviders.value` in its constructor. That flow was a `StateFlow` seeded with `null`
  while the real value arrived asynchronously from DataStore, so the constructor always read the placeholder and cached
  an empty list, permanently. The next edit then persisted a list built on that empty baseline, **destroying** the
  stored providers rather than merely failing to display them — and orphaning their ~400 MB files in internal storage.
- **A local provider's selection could never be restored.** `MapViewModel.loadPersistedMapType()` matched the saved
  selection with `it.urlTemplate == savedCustomUrl && isValidTileUrlTemplate(savedCustomUrl)`. An imported MBTiles
  provider has an empty `urlTemplate` and is persisted by its `file://` URI, which contains no `{z}/{x}/{y}`, so both
  conditions were always false. The fallback branch then actively cleared the stored preference. The renderer
  (`MapView.kt`) resolved the same selection correctly, so the two paths disagreed.

Fix:

- `MapTileProviderPrefs.customTileProviders` and `GoogleMapsPrefs.selectedCustomTileUrl` /
  `selectedGoogleMapType` are plain `Flow`s instead of `StateFlow`s. A `StateFlow` has to invent an initial value, and
  callers cannot tell that placeholder apart from a genuine "nothing saved".
- `CustomTileProviderRepositoryImpl` keeps a nullable cache — `null` meaning "not read yet" — publishes only once the
  store has answered, and serializes read-modify-write cycles behind a `Mutex`.
- Selection resolution lives in `CustomTileProviderConfig.selectionKey` / `matchesSelection`, used by both the restore
  path and the renderer so they cannot drift apart again.
- `MapViewModel.restoreMapSelection()` awaits both stores before deciding, and only clears a stored selection once the
  provider list is known to be loaded.
- Fixed along the way: editing a provider left the persisted selection pointing at a stale key, so the layer was
  dropped on the following start.

Also removed: `feature/map/src/androidUnitTestGoogle/` (`MapViewModelTest`, `MBTilesProviderTest`). Those files sat in
a source set no Gradle task builds, targeted an `org.meshtastic.feature.map` package that does not exist, and used a
`MapViewModel` constructor signature several parameters out of date. They never compiled or ran — which is how this
bug shipped. Replacement tests live in `androidApp/src/testGoogle/`, the source set that is actually wired up.

### 2. Offline map priority (fork behaviour)

`MapViewModel.restoreMapSelection()` selects the first local provider when there is no usable saved selection, so the
app opens on the offline map with no network and no user action.

Trade-off, stated plainly: while an MBTiles provider exists, a deliberate switch to a Google base map does not survive
a restart — the offline layer wins again on the next launch. That is intended here (the app must always come up usable
in the forest). To soften it, drop the `offlineProvider` branch in `restoreMapSelection()`; restoring an explicit saved
selection keeps working without it.
