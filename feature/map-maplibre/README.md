# `:feature:map-maplibre`

## Overview

The MapLibre rendering surfaces, used by the `fdroid` flavor and by Desktop. The `google` flavor renders the same
features through Google Maps instead and never depends on this module.

Its own module rather than source sets inside `:feature:map`, because that module compiles into **both** Android
flavors — MapLibre living there would pull maplibre-native's `.so` payload into the Play Store build for nothing.

Everything that renders is `commonMain`, and Android and Desktop share those files verbatim. The platform source
sets hold only two small `expect`/`actual` pairs: `MapLibreRuntime` (whether the native renderer is available) and
`OfflineTerrainStorage` (where downloaded terrain is kept).

## What it renders

| Surface | Entry point |
|---|---|
| Main map — nodes, waypoints, clusters, geofences, controls | `MapLibreMapViewProvider` → `MeshMap` |
| Per-node position track | `MapLibreNodeTrackMap` (`NodeTrackMap.kt`) |
| Traceroute route | `MapLibreTracerouteMap` (`SecondaryMaps.kt`) |
| Discovery scan map | `MapLibreDiscoveryMap` (`SecondaryMaps.kt`) |
| Node-detail mini-map | `MapLibreInlineMap` (`SecondaryMaps.kt`) |

The secondary surfaces share the pieces in `SecondaryMapScaffold.kt` (`SecondaryMapSurface`, `SecondaryMapChrome`,
`FitBoundsOnceVisible`) and `SecondaryMapControls`, so they cannot drift apart.

## Structure

- **`geojson/`** — mesh data as GeoJSON feature collections. `FeatureSource` rebuilds a collection only when its keys
  change, so panning does not re-serialize every node.
- **`layers/`** — the MapLibre layers those sources feed: node chips, the just-heard pulse, waypoints, precision
  circles, traceroute lines, and the user's imported overlays.
- **`style/`** — `Basemaps` (vector styles plus the shared raster catalog), `MapOverlays`, `MapColors`.
- **`component/`** — the map's own chrome: basemap menu, layers sheet, zoom, ornaments, dialogs.
- **`terrain/`** — the one downloaded offline-terrain region (`OfflineTerrainRepository`, `OfflineTerrainRegion`),
  stored through `:feature:map-terrain`'s `TerrainTileStore`. `TerrainLayers` draws its hillshade and contours when
  the region covers the viewport, and `OfflineTerrainSection` downloads it from the layers sheet.

## What lives in `:feature:map` instead

Anything both renderers must agree on, so a difference between the flavors is a test failure rather than a bug report:
`BaseMapViewModel`, `MapNodePolicy`, `MapBounds`, `MapTimeWindows`, `EditWaypointDialog`, the `BasemapMenu` and
overlay toggles, the KML→GeoJSON converter, and the tile catalog (`MapTileCatalogue`, `RasterTileSpec`) with the
custom tile-source store and editor.

## Things that will bite

- **A raster basemap needs a style that declares `glyphs`.** A style with none can load no font, and the first text
  symbol layer that fails takes down every layer added after it, cluster bubbles included, so the mesh draws nothing
  on a raster basemap. `RasterBaseStyle` declares them, and `MapLibreMapTest` asserts every basemap's style does.
- **Hillshade must stay Terrarium-encoded.** MapLibre defaults raster-DEM sources to Mapbox Terrain-RGB and the
  mismatch is silent: shading still renders, it is just wrong.
- **Offline packs only download on Android.** The API compiles on every target and on Desktop will create a pack,
  list it, and never fetch a tile — hence `offlineMapsSupported`, off by default.
- **`mbtiles://` needs a file that exists.** Pointing the native MBTiles source at a missing path aborts the process
  from a native thread, with no Kotlin frame to catch.
- **Node chips are rasterized, not drawn as text.** The basemap glyphs have no emoji, and a short name can be one, so
  a text layer over a tinted `drawAsSdf` sprite would drop those labels. `NodeChipLayer` explains the rest.

## Testing

`:feature:map-maplibre:allTests` — pure logic only, no renderer: basemap registry invariants, cluster membership,
node/waypoint feature building, chip ranking, contour features, offline tile estimates, and the offline terrain
manifest and store round-trips. Compose UI tests run in `jvmTest` only.
