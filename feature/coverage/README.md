# feature:coverage

Computes RF coverage in-process with
[`org.meshtastic:kp1812`](https://github.com/meshtastic/kp1812), an implementation of
Recommendation ITU-R P.1812, over Mapterhorn terrain. It works offline once the terrain is
cached.

## Where it runs

Every map host runs the same flow. Each caller below passes this module's `rememberCoverageEstimate()`
to `feature:map`'s `SitePlannerHost` as its `estimate`. `SitePlannerHost` shows the form, runs the
estimate behind a progress dialog, and hands the resulting GeoJSON back so the host can add it as a
map layer and move the map to the transmitter.

| Host | Caller |
| --- | --- |
| Android, Google flavor | `MapView` |
| Android, F-Droid flavor | `SitePlannerSlot` |
| Desktop | `DesktopSitePlannerSlot` |

## Shape

```text
SitePlannerParams -> Site + ElevationSource -> LocalCoverage.sweepGrid() -> CoverageGrid -> toGeoJson(CoverageStyle)
                                                       |
                                                       +-- org.meshtastic:kp1812 (ITU-R P.1812)
```

The model, the `ElevationSource` seam, and the GeoJSON export live in `commonMain`. The platform
source sets hold only the terrain cache location (`coverageTerrainDirectory()`) and, in `jvmMain`,
the `java.awt` demo renderer `CoverageDemo`. Terrain is cached through `feature:map-terrain`'s
`TerrainTileStore` under `terrain/coverage` in the app's storage (`~/.meshtastic/terrain/coverage`
on Desktop), apart from downloaded offline map regions. `ElevationSource` is a single suspend method: the app
backs it with Mapterhorn tiles, and tests back it with a lambda, so the suite needs no network,
`WebView`, or terrain download.

P.1812 is a different model from the hosted planner's SPLAT! ITM, so predictions don't match it
pixel for pixel.

## Tests

The tests are behavioral. `kp1812` checks the model against the ITU reference itself, so these
check that this module drives it correctly:

- Signal decays with distance over flat ground.
- A 400 m ridge shadows what's behind it.
- More transmit power reaches at least as far.
- `reachable` agrees with the receiver sensitivity.
- Every bearing contributes equally to the sweep.
- The geodesy round-trips.
- The polar sweep resamples onto a grid without moving the prediction.
- Every palette the planner offers resolves, and transparency maps to opacity.
- The terrain zoom drops for a wide disc, so its tiles fit the cache.
- A canceled terrain fetch restarts for a caller that is still waiting.

Run them with `./gradlew :feature:coverage:allTests`. `./gradlew :feature:coverage:coverageDemo`
runs a real prediction against Mapterhorn terrain and writes a PNG and the GeoJSON; pass its
arguments with `-PdemoArgs="..."`.
