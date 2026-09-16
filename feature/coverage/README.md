# feature:coverage — SPIKE

Local RF coverage, replacing the headless-WebView hand-off to the hosted Site Planner.

## What it replaces

| Host | Today | With this |
| --- | --- | --- |
| Android | 319-line hidden `WebView` loading site.meshtastic.org, JS bridge, 45 s timeout, needs network | in-process, offline once terrain is cached |
| Desktop | opens a browser; user **exports a `.geojson` and re-imports it by hand** | in-process |

`SitePlannerRunner.kt` is load-bearing in ways that read as a warning: the WebView must be
`alpha(0)` *but still attached and 280 dp* or WebGL never gets a context; it carries a deferred
retry for the system-WebView provider-update race; and `shouldOverrideUrlLoading` locks
navigation to the planner's origin so nothing else can reach `onCoverage`. All of that exists to
work around running a browser to do arithmetic.

## Shape

```
Site + ElevationSource ──► LocalCoverage.sweep() ──► Coverage(points: List<CoveragePoint>)
                                  │
                                  └─ org.meshtastic:kp1812 (ITU-R P.1812)
```

`ElevationSource` is a single suspend method. The app backs it with `feature/map-terrain`'s
Mapterhorn tiles; tests back it with a lambda, which is why the suite needs no network, no
WebView and no terrain download.

## Scope and caveats

- **`jvm()` only.** `kp1812` publishes no `androidTarget` — Android is meant to consume its `jvm`
  artifact, the same choice `kzstd` makes — and proving that resolution path is a separate
  question from proving the model works. Desktop is also where the current experience is worst.
- **Different model.** P.1812 is not ITM. Predictions will not match the hosted planner pixel for
  pixel, and that is expected rather than a defect.
- **No UI wiring.** This is computation plus tests. Replacing `DesktopSitePlannerSlot` is the next
  step and needs the full android baseline run.
- **`kp1812` is unpublished**, so the spike resolves it from `mavenLocal` via the repo's existing
  `-PuseMavenLocal` flag. Publish it with `./gradlew publishJvmPublicationToMavenLocal
  publishKotlinMultiplatformPublicationToMavenLocal` from the sibling checkout.

## Tests

Behavioural, not conformance — `kp1812` already checks itself against the ITU reference:

- signal decays with distance over flat ground
- a 400 m ridge measurably shadows what is behind it
- more transmit power reaches at least as far
- `reachable` agrees with the receiver sensitivity
- the sweep covers every bearing
- the geodesy round-trips
