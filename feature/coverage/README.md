# feature:coverage

Computes RF coverage in-process with
[`org.meshtastic:kp1812`](https://github.com/meshtastic/kp1812), an implementation of
Recommendation ITU-R P.1812, over Mapterhorn terrain. It works offline once the terrain is
cached.

## Where it runs

| Host | Coverage |
| --- | --- |
| Desktop | `DesktopSitePlannerSlot` runs `LocalCoverage` and adds the result to the map as a GeoJSON layer |
| Android | Not wired. Both flavors still drive the hosted Site Planner in a hidden `WebView` (`SitePlannerRunner`) |

## Shape

```text
Site + ElevationSource -> LocalCoverage.sweepGrid() -> CoverageGrid -> toGeoJson(CoverageStyle)
                                  |
                                  +-- org.meshtastic:kp1812 (ITU-R P.1812)
```

The model, the `ElevationSource` seam, and the GeoJSON export live in `commonMain`. Only the
`java.awt` demo renderer is in `jvmMain`. `ElevationSource` is a single suspend method: the app
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
