---
title: Developer Guide
layout: default
nav_order: 2
has_children: true
---

# Developer Guide

Technical documentation for contributing to the Meshtastic Android and Desktop app.

---

## Before You Open a PR

Things that trip up first-time contributors — check these before requesting review:

- **Formatting passes** — run `./gradlew spotlessApply` to auto-format, then verify with `spotlessCheck`
- **Detekt passes** — run `./gradlew detekt` and fix all reported issues
- **All tests pass** — run `./gradlew test allTests` (both are needed: `test` covers Android-only modules, `allTests` covers KMP)
- **Screenshot tests pass** — if you touched any Compose UI, run `./gradlew :screenshot-tests:validateDebugScreenshotTest` and update reference images if needed
- **Protos are an external dependency** — protobuf models come from the `org.meshtastic:protobufs` Maven artifact (pinned in `gradle/libs.versions.toml`); change protos upstream and bump the version, never edit generated code locally
- **Docs updated** — if you changed user-visible UI, update the corresponding page under `docs/en/user/`
- **Previews updated** — if you changed UI composables, update the corresponding `*Previews.kt` file and the screenshot-test baselines
- **Branch naming** — branches must start with `feat/`, `fix/`, `chore/`, `docs/`, `build/`, `ci/`, `refactor/`, `test/`, or `deps/`

---

## What's New for Developers

<!-- DEV_WHATS_NEW_START -->
<!-- Add new entries at the top. Format:
**Month YYYY** — [Page or area](relative/path) — One sentence on what changed architecturally or procedurally.
Keep the last 5–8 entries and trim older ones from the bottom.
-->

**August 2026** — [Documentation Style](developer/documentation-style) — New page: the house style guide for `docs/en/` prose, with rule IDs, a project word list, and the reasoning behind each convention.

**August 2026** — Map tile sources are one shared catalogue in `feature/map` (`MapTileCatalogue`, `RasterTileSpec`), so both flavours draw the same raster base maps and overlays from one definition; the Google map fetches them through an OkHttp disk cache rather than `UrlTileProvider`. The custom tile-source editor, its store and its config moved to `feature/map/commonMain` as well, which is what gives Desktop custom sources. Offline pack downloads are gated on `offlineMapsSupported`: the MapLibre offline API compiles everywhere but silently downloads nothing on Desktop. KML import parses through xmlutil in `feature/map/commonMain` (`KmlToGeoJson`) rather than `XmlPullParser`, so the converter and its tests are common code; recognising a KMZ and unpacking it stays with the host. Both maps now draw an imported feature's own icon: the converter reads `<IconStyle><Icon><href>` into an `icon-url` property, the Google map resolves it (including a KMZ's packed images), and MapLibre loads it as a style image. KMZ `<GroundOverlay>` images drape at their `<LatLonBox>` (rotation included) on both maps — MapLibre via an `ImageSource` quad, Google via `GroundOverlayOptions` (#3786).

**August 2026** — New module `feature/map-maplibre`: the F-Droid flavor and Desktop now render every map surface (main map, node track, traceroute, discovery, inline mini-map) through `maplibre-compose` from one multiplatform module, and `osmdroid` is gone. The July entry below describes the renderer it replaced. `EditWaypointDialog` moved to `feature/map/commonMain` on Material 3 date/time pickers so Desktop can create waypoints; the shared rules the two renderers must agree on live in `MapNodePolicy`, `MapBounds` and `MapTimeWindows`, and the position-precision radius in `core/model/util/PositionPrecision.kt`.

**August 2026** — Android Auto removed from all build variants (#6779). `feature/car` is gone — the module, its Car App Library dependencies, the `automotive_app_desc.xml` manifest entry and the `google` flavor's `FlavorModule` registration.

**August 2026** — [Testing](developer/testing) — CI gained a fourth runner tier: `ubuntu-slim` (#6674, #6677) now carries the lightweight jobs. It is single-CPU, unprivileged, x64-only and capped at 15 minutes, so anything needing `sudo`, Docker or a full-history clone stays on `ubuntu-24.04-arm`. Picking rules are in `.github/instructions/ci-workflows.instructions.md`.

**August 2026** — Flatpak sources are generated inside each architecture's own offline build (#6919) rather than committed, and the flatpak-sources plugin resolves platform dependencies transitively (0.2.x), so the hand-tracked entries are gone.

**July 2026** — [Test Builds & Obtainium](developer/test-builds) — New page, replacing the root `obtainium-test-builds.md`. Distributable Obtainium configurations now live in `obtainium/` (importable export, one-tap link generator, config-site submission).

**July 2026** — Map layer stack (`MapLayer.kt`, `MapLayersManager`, GeoJSON/KML import, Site Planner) extracted from the Google flavor into shared `androidApp/src/main` source (#6148) — F-Droid now renders imported overlays via a new OSMdroid-based renderer, so both flavors compile one implementation.

**July 2026** — [Persistence](developer/persistence) — Local Mesh Discovery sessions and cached `msh.to` device links now persist to Room (`DiscoverySessionEntity`, `DiscoveryPresetResultEntity`, `DiscoveredNodeEntity`, `DeviceLinkEntity`).

**June 2026** — [Architecture](developer/architecture) / [Codebase](developer/codebase) — Protos migrated from the `core/proto` git submodule to the `org.meshtastic:protobufs` Maven artifact; there is no longer a local proto module to build or sync.

**June 2026** — AIDL/`IMeshService` removed (#5586). The mesh service is now in-process only, driven entirely through `RadioController` — no cross-process binder, no `aidl` stubs.

**June 2026** — [Testing](developer/testing) — Split the screenshot pipeline: the new generate-only `:docs-screenshots` module holds doc-framed compositions, while `:screenshot-tests` stays the CI visual-regression gate — so reframing a doc image no longer churns a test baseline.


<!-- DEV_WHATS_NEW_END -->

