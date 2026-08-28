# `:feature:map-maplibre`

## Overview

The MapLibre rendering surfaces, used by the `fdroid` flavor and by Desktop. The `google` flavor renders the same
features through Google Maps instead and never depends on this module.

Its own module rather than source sets inside `:feature:map`, because that module compiles into **both** Android
flavors — MapLibre living there would pull maplibre-native's `.so` payload into the Play Store build for nothing.

Everything here is `commonMain`. Android and Desktop share these files verbatim; there is no per-platform source set.

## What it renders

| Surface | Entry point |
|---|---|
| Main map — nodes, waypoints, clusters, geofences, controls | `MapLibreMapViewProvider` → `MeshMap` |
| Per-node position track | `MapLibreNodeTrackMap` (`NodeTrackMap.kt`) |
| Traceroute route | `MapLibreTracerouteMap` (`SecondaryMaps.kt`) |
| Discovery scan map | `MapLibreDiscoveryMap` (`SecondaryMaps.kt`) |
| Node-detail mini-map | `MapLibreInlineMap` (`SecondaryMaps.kt`) |

The secondary surfaces share `SecondaryMapScaffold` and `SecondaryMapControls` so they cannot drift apart.

## Structure

- **`geojson/`** — mesh data as GeoJSON feature collections. `FeatureSource` rebuilds a collection only when its keys
  change, so panning does not re-serialize every node.
- **`layers/`** — the MapLibre layers those sources feed: node chips, the just-heard pulse, waypoints, precision
  circles, traceroute lines, and the user's imported overlays.
- **`style/`** — `Basemaps` (vector styles plus the shared raster catalogue), `MapOverlays`, `MapColors`.
- **`component/`** — the map's own chrome: basemap menu, layers sheet, zoom, ornaments, dialogs.

## What lives in `:feature:map` instead

Anything both renderers must agree on, so a difference between the flavors is a test failure rather than a bug report:
`BaseMapViewModel`, `MapNodePolicy`, `MapBounds`, `MapTimeWindows`, `EditWaypointDialog`, the `BasemapMenu` and
overlay toggles, the KML→GeoJSON converter, and the tile catalogue (`MapTileCatalogue`, `RasterTileSpec`) with the
custom tile-source store and editor.

## Things that will bite

- **A raster basemap needs a style that declares `glyphs`.** A style with none can load no font, and the first text
  symbol layer that fails takes down every layer added after it — which on a raster basemap meant the mesh drew
  nothing at all, cluster bubbles included. `RasterBaseStyle` exists for this, and a test asserts it.
- **Hillshade must stay Terrarium-encoded.** MapLibre defaults raster-DEM sources to Mapbox Terrain-RGB and the
  mismatch is silent: shading still renders, it is just wrong.
- **Offline packs only download on Android.** The API compiles on every target and on Desktop will create a pack,
  list it, and never fetch a tile — hence `offlineMapsSupported`, off by default.
- **`mbtiles://` needs a file that exists.** Pointing the native MBTiles source at a missing path aborts the process
  from a native thread, with no Kotlin frame to catch.
- **`maplibre-compose` 0.15.0's `drawAsSdf` is broken.** `ImageManager.acquirePainter` computes `toSdf()`, stores it
  where nothing reads it, and uploads the unconverted bitmap while telling MapLibre to read it as an SDF. Nothing
  here uses it; `NodeChipLayer` says so too.

## Testing

`:feature:map-maplibre:allTests` — pure logic only, no renderer: basemap registry invariants, cluster membership,
node/waypoint feature building, chip ranking, and offline tile estimates.
