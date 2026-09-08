# `offline/pmtiles`

## Overview

Offline vector regions for the **Google** flavor — the counterpart to the MapLibre flavor's native `OfflinePack`
API, which Google Maps has no equivalent of (its `TileProvider` is raster-only). Ported from the sibling iOS app's
PMTiles-based offline map (`Meshtastic/Helpers/Map/PMTilesExtractor.swift`, `PMTilesMapView.swift`): decode straight
to native map shapes rather than rasterizing, so the offline layer draws in the same coordinate space as node
markers with no compositing seam.

## Pipeline

1. **`PmTilesDailyBuild`** resolves the current `build.protomaps.com/{date}.pmtiles` URL (probing backward up to 16
   days for the newest one that actually exists).
2. **`OfflineRegionExtractor`** enumerates the bbox/zoom-range's tiles (`OfflineRegionTileSet`) and fetches each one
   individually through `ch.poole.geo.pmtiles.Reader`, which range-requests only that tile's own bytes out of the
   multi-gigabyte remote file — never the whole thing.
3. **`OfflineVectorArchive`** writes the fetched tiles into a local `.mbtiles` archive (`tiles`/`metadata` tables,
   `format=pbf`) — the same container `MBTilesProvider` already reads for raster imports, just vector this time.
   Stored uncompressed, not gzip: simpler, at the cost of more disk per tile (see "Deferred" below).
4. **`OfflineVectorRenderer`** decodes a rendered tile's `water`/`roads`/`boundaries` layers (`VectorTile.kt` +
   `MvtDecoder.kt`) into `LatLng` rings, cached per tile so panning within the same tiles doesn't redecode.
5. **`OfflineVectorOverlay`** (a `@Composable`) draws those rings as native `Polygon`/`Polyline`.

## Why this shape, not iOS's

- **MVT parsing**: hand-written `@Serializable` classes decoded with `kotlinx-serialization-protobuf`, not a
  vendored parser — the natural Java vector-tile libraries on GitHub (e.g. `ElectronicChartCentre/java-vector-tile`)
  were never published to Maven Central, and JitPack is a supply-chain trade this ~60-line schema doesn't need to
  make. `ch.poole.geo.pmtiles:Reader` (MIT-licensed) *is* on Maven Central and handles the PMTiles container format,
  so that piece isn't hand-rolled.
- **Local storage**: a plain `.mbtiles` SQLite archive, not iOS's own PMTiles-shaped local file. PMTiles' cleverness
  (a single HTTP-rangeable remote file) doesn't buy anything once the tiles are already extracted locally; SQLite is
  the format this codebase already speaks (`MBTilesProvider`) and any generic MBTiles tool can open what this writes.
- **No geometry simplification/road-stitching pass** (iOS's `PMTilesMapView.swift:247-321`). MVT tiles are already
  simplified per zoom level by the source, and Google's classic renderer tolerates more overlays than SwiftUI `Map`
  does — legible without it for a first pass, but the win (fewer, longer polylines) is real and worth doing later.
- **One HTTP request per tile**, not iOS's coalesced-byte-range batching. This is why `MAX_TILES_PER_REGION` is
  2,000 here against iOS's 600,000 — at one round trip per tile, anything near that scale would be impractically
  slow on a phone connection. Coalescing adjacent tiles' byte ranges into batched requests is the natural way to
  raise this cap.

## Deferred (tracked here, not hidden)

- **Compression**: tiles are gunzipped once at download time and stored raw. Storing gzip (like every other MBTiles
  vector-tile archive) and decompressing at render time would meaningfully shrink a downloaded region.
- **Polygon holes**: every ring MVT returns — hole or exterior — is drawn as its own independent `Polygon`. A real
  lake-with-island therefore double-draws over the island rather than cutting it out. Grouping rings by winding
  direction (spec section 4.3.3.3) into proper polygon/hole sets would fix this.
- **Auto-activation on connectivity loss**: `offlineOverlayEnabled` is a manual toggle today. Wiring it to
  `mapNetworkAvailable` (from the sibling offline-fallback PR) so a downloaded region draws itself the instant the
  network drops — matching what that PR already does on MapLibre and what iOS does for its whole offline layer — is
  a small follow-up once both land; kept manual here so this PR doesn't depend on that one merging first.
- **Terrain/hillshade**: iOS also offers an offline terrain layer via the same bbox-extraction technique against a
  different PMTiles source (Mapterhorn). Not attempted here.

## Legal

- `ch.poole.geo.pmtiles:Reader` — MIT license.
- Rendered data is OpenStreetMap-derived via Protomaps; the archive's `metadata` table and the in-app attribution
  both credit "© OpenStreetMap contributors, © Protomaps" (`OfflineRegionExtractor.ATTRIBUTION`).
- `build.protomaps.com`'s daily-build endpoint has no documented rate limit or terms distinct from the general
  Protomaps project — unlike the separate, documented `api.protomaps.com` hosted tile API. Worth a periodic check
  that this is still an acceptable way to source the data; the extraction is a one-time-per-region slice, not
  live traffic on every app launch.

## Testing

`androidApp:testGoogle` — `MvtDecoderTest` (hand-encoded protobuf bytes, not a vendored fixture or a round-trip
through the same encoder under test — see its own doc comment), `WebMercatorTileMathTest`, `OfflineRegionTileSetTest`.
No test drives the network extractor itself; it's a thin loop over already-tested pieces (`Reader`, `MvtDecoder`,
`OfflineVectorArchive`).
