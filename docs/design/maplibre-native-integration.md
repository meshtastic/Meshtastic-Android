# MapLibre Native integration for F-Droid flavor (replace osmdroid)

## Overview

The F-Droid variant currently uses osmdroid, which is archived and no longer actively maintained. This document proposes migrating the F-Droid flavor to MapLibre Native for a modern, actively maintained, fully open-source mapping stack compatible with F-Droid constraints.

Reference: MapLibre Native (BSD-2-Clause) — `org.maplibre.gl:android-sdk` [GitHub repository and README](https://github.com/maplibre/maplibre-native). The README shows Android setup and basic usage, e.g.:

```gradle
implementation 'org.maplibre.gl:android-sdk:12.1.0' // use latest stable from releases
```

Releases page (latest): see Android release notes (e.g., android-v12.1.0) in the repository releases feed.


## Goals

- Replace osmdroid in the `fdroid` flavor with MapLibre Native.
- Maintain core map features: live node markers, waypoints, tracks (polylines), bounding-box selection, map layers, and user location.
- Stay F-Droid compatible: no proprietary SDKs, no analytics, and tile sources/styles that don’t require proprietary keys.
- Provide a path for offline usage (caching and/or offline regions) comparable to current expectations.
- Keep the Google flavor unchanged (continues using Google Maps).


## Non-goals

- Changing the Google flavor map implementation.
- Shipping proprietary tile styles or API-key–gated providers by default.
- Reworking unrelated UI/UX; only adapt what’s necessary for MapLibre.


## Current state (F-Droid flavor)

The `fdroid` flavor is implemented with osmdroid components and includes custom overlays, caching, clustering, and tile source utilities. Key entry points and features include (non-exhaustive):

- `feature/map/src/fdroid/kotlin/org/meshtastic/feature/map/MapView.kt`: Composable map screen hosting an `AndroidView` of `org.osmdroid.views.MapView`.
- `feature/map/src/fdroid/kotlin/org/meshtastic/feature/map/MapViewWithLifecycle.kt`: Lifecycle-aware creation and configuration of osmdroid `MapView`, including zoom bounds, DPI scaling, scroll limits, and user-agent config.
- `feature/map/src/fdroid/kotlin/org/meshtastic/feature/map/node/NodeMapScreen.kt`: Node-focused map screen using fdroid map utilities.
- `feature/map/src/fdroid/kotlin/org/meshtastic/feature/map/MapViewExtensions.kt`: Helpers to add copyright, overlays (markers, polylines, scale bar, gridlines).
- `feature/map/src/fdroid/kotlin/org/meshtastic/feature/map/model/*`: Tile source abstractions and WMS helpers (e.g., NOAA WMS), custom tile source with auth, and marker classes.
- `feature/map/src/fdroid/java/org/meshtastic/feature/map/cluster/*`: Custom clustering (RadiusMarkerClusterer, MarkerClusterer, etc.).
- Caching/downloading logic via osmdroid `CacheManager`, `SqliteArchiveTileWriter`, and related helpers.

Build dependencies:

- `fdroidImplementation(libs.osmdroid.android)` and `fdroidImplementation(libs.osmdroid.geopackage)` in `app/build.gradle.kts`.


## Proposed architecture (F-Droid flavor on MapLibre Native)

At a high level, we replace the fdroid/osmdroid source set with a MapLibre-based implementation using `org.maplibre.android.maps.MapView` hosted in `AndroidView` for Compose interop. Feature parity is achieved by translating current overlays/utilities to MapLibre’s source/layer model.

### Parity with Google Maps flavor (feature-by-feature)

This section captures the Google flavor’s current capabilities and how we’ll provide equivalent behavior with MapLibre Native for the F-Droid flavor.

- Map types (Normal/Satellite/Terrain/Hybrid)
  - Google: `MapTypeDropdown` switches `MapType` (Normal, Satellite, Terrain, Hybrid).
  - MapLibre: Provide a “Style chooser” mapped to a set of styles (e.g., vector “basic/streets”, “terrain” style when available, “satellite”, and “hybrid” if a provider is configured). Implementation: swap style URLs at runtime. Note: Satellite/Hybrid require non-proprietary providers; we will ship only F-Droid-compliant defaults and allow users to add custom styles.

- Custom raster tile overlays (user-defined URL template)
  - Google: `TileOverlay` with user-managed providers and a manager sheet to add/edit/remove templates.
  - MapLibre: Add `RasterSource` + `RasterLayer` using `{z}/{x}/{y}` URL templates. Keep the same management UI: add/edit/remove templates, persist selection, z-order above/below base style as applicable.

- Custom map layers: KML and GeoJSON import
  - Google: Imports KML (`KmlLayer`) and GeoJSON (`GeoJsonLayer`) with visibility toggles and persistence.
  - MapLibre: Native `GeoJsonSource` for GeoJSON; for KML, implement conversion to GeoJSON at import time (preferred) or a KML renderer. Keep the same UI: file picker, layer list with visibility toggles, and persistence. Large layers should be loaded off the main thread.

- Clustering of node markers
  - Google: Clustering via utility logic and a dialog to display items within a cluster.
  - MapLibre: Enable clustering on a `GeoJsonSource` (cluster=true). Use styled `SymbolLayer` for clusters and single points. On cluster tap, either zoom into the cluster or surface a dialog with the items (obtain children via query on `cluster_id` using runtime API or by maintaining an index in the view model). Match the existing UX where feasible.

- Marker info and selection UI
  - Google: `MarkerInfoWindowComposable` for node/waypoint info; cluster dialog for multiple items.
  - MapLibre: Use map click callbacks to detect feature selection (via queryRenderedFeatures) and show Compose-based bottom sheets or dialogs for details. Highlight the selected feature via data-driven styling (e.g., different icon or halo) to mimic info-window emphasis.

- Tracks and polylines
  - Google: `Polyline` for node tracks.
  - MapLibre: `GeoJsonSource` + `LineLayer` for tracks. Style width/color dynamically (e.g., by selection or theme). Maintain performance by updating sources incrementally.

- Location indicator and follow/bearing modes
  - Google: Map UI shows device location, with toggles for follow and bearing.
  - MapLibre: Use the built-in location component to display position and bearing. Implement follow mode (camera tracking current location) and toggle bearing-follow (bearing locked to phone heading) via camera updates. Respect permissions as in the current flow.

- Scale bar
  - Google: `ScaleBar` widget (compose).
  - MapLibre: Implement a Compose overlay scale bar using map camera state and projection (compute meters-per-pixel at latitude; snap to nice distances). Show/hide per the current controls.

- Camera, gestures, and UI controls
  - Google: `MapProperties`/`MapUiSettings` for gestures, compass, traffic, etc.
  - MapLibre: Use MapLibre’s `UiSettings` and camera APIs to align gesture enablement. Provide a compass toggle if needed (Compose overlay or style-embedded widget).

- Map filter menu and other HUD controls
  - Google: Compose-driven “filter” and “map type” menus; custom layer manager; custom tile provider manager.
  - MapLibre: Reuse the same Compose controls; wire actions to MapLibre implementations (style swap, raster source add/remove, layer visibility toggles).

- Persistence and state
  - Keep the same persistence strategy used by the Google flavor for selected map type/style, custom tile providers, and imported layers (URIs, visibility). Ensure parity in initial load behavior and error handling.

Gaps and proposed handling:
- Satellite/Hybrid availability depends on F-Droid-compliant providers; we will ship only compliant defaults and rely on user-provided styles for others.
- KML requires conversion or a dedicated renderer; we will implement KML→GeoJSON conversion at import time for parity with visibility toggles and persistence.

### Core components

- Lifecycle-aware `MapView` wrapper (Compose): Use `AndroidView` to create and manage `org.maplibre.android.maps.MapView` with lifecycle forwarding (onStart/onStop/etc.), mirroring the current `MapViewWithLifecycle.kt` responsibilities.
- Style and sources:
  - Default dev style: `https://demotiles.maplibre.org/style.json` for initial bring-up (public demo). This avoids proprietary keys and is fine for development. Later, we can switch to a more appropriate default for production.
  - Raster tile support: Add `RasterSource` for user-provided raster tile URL templates (equivalent to existing “custom tile provider URL” functionality).
  - Vector data for app overlays: Use `GeoJsonSource` for nodes, waypoints, and tracks, with appropriate `SymbolLayer` and `LineLayer` styling. Polygons (e.g., bounding box) via `FillLayer` or line+fill combo.
- Clustering:
  - Use `GeoJsonSource` with clustering enabled for node markers (MapLibre supports clustering at the source level). Configure cluster radius and properties to emulate current behavior.
- Location indicator:
  - Use the built-in location component in MapLibre Native to show the device location and bearing (when permitted).
- Gestures and camera:
  - MapLibre’s `UiSettings` and camera APIs mirror Mapbox GL Native; expose zoom, bearing, tilt as needed to match osmdroid behavior.
- Permissions:
  - Retain existing Compose permission handling; wire to enable/disable location component.


## Offline and caching

MapLibre Native offers mechanisms similar to Mapbox GL Native for tile caching and offline regions. We will:

1. Start with default HTTP caching (on-device tile cache) to improve repeated region performance.
2. Evaluate and, if feasible, implement offline regions for vector tiles (download defined bounding boxes and zoom ranges). This would replace osmdroid’s `CacheManager` flow and UI. If vector offline proves complex, a phase may introduce raster MBTiles as an interim solution (note: MapLibre Native does not directly read MBTiles; an import/conversion path or custom source is needed if we choose that route).
3. Preserve current UX affordances: bounding-box selection for offline region definition, progress UI, cache size/budget, purge actions.

Open question: confirm current MapLibre Native offline APIs and recommended approach on Android at the chosen SDK version. If upstream guidance prefers vector style packs or a particular cache API, we’ll align to that.


## Tile sources, styles, and F-Droid compliance

- Default dev style: MapLibre demo style (`demotiles.maplibre.org`) for development/testing only.
- Production defaults must:
  - Avoid proprietary SDKs and keys.
  - Respect provider TOS (e.g., OSM or self-hosted tiles). Consider self-hosted vector tiles (OpenMapTiles) or community-friendly providers with clear terms for mobile clients.
- Custom tile provider URL:
  - Support raster custom URLs via `RasterSource` in the style at runtime.
  - For vector custom sources, we’ll likely require a user-supplied style JSON URL (vector styles are described by style JSONs that reference vector sources); we can enable “Custom style URL” input for advanced users.


## Build and dependencies

- Add MapLibre Native to the `fdroid` configuration in `app/build.gradle.kts`:
  - `fdroidImplementation("org.maplibre.gl:android-sdk:<latest>")`
- Remove:
  - `fdroidImplementation(libs.osmdroid.android)`
  - `fdroidImplementation(libs.osmdroid.geopackage)` (and related exclusions)
- Native ABIs:
  - App already configures `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`; MapLibre Native provides native libs accordingly, so the existing NDK filters should be compatible.
- Min/target SDK:
  - MapLibre Native minSdk is compatible (App targets API 26+; verify exact minSdk for selected MapLibre version).


## Migration plan (phased)

Phase 1: Bring-up and parity core
- Create `fdroid`-only MapLibre `MapView` composable using `AndroidView` and lifecycle wiring.
- Initialize `MapLibre.getInstance(context)` and load a simple style.
- Render nodes and waypoints using `GeoJsonSource` + `SymbolLayer`.
- Render tracks using `GeoJsonSource` + `LineLayer`.
- Replace location overlay with MapLibre location component.
- Replace map gestures and scale bar equivalents (either via style or simple Compose overlay).

Phase 2: Clustering and UI polish
- Implement clustering with `GeoJsonSource` clustering features.
- Style cluster circles/labels; match existing look/feel as feasible.
- Restore “map filter” UX and marker selection/infowindows (Compose side panels/dialogs).

Phase 3: Offline and cache UX
- Implement region selection overlay as style layers (polygon/line) and coordinate it with cache/offline manager.
- Add offline region creation, progress, and management (estimates, purge, etc.).

Phase 4: Cleanup
- Remove osmdroid-specific code: tile source models, WMS helpers (unless replaced with MapLibre raster sources), custom cluster Java classes, cache manager extensions.


## Risks and mitigations

- Native size increase: MapLibre includes native libs; monitor APK size impact per ABI and leverage existing ABI filters.
- GPU driver quirks: MapLibre uses OpenGL/Metal (platform dependent); test across representative devices/ABIs.
- Offline complexity: Vector offline requires careful style/source handling; mitigate via phased rollout and clear user-facing expectations.
- Tile provider TOS: Ensure defaults are compliant; prefer self-hosted or community-safe options.
- Performance: Reassess clustering and update strategies (debounce updates, differential GeoJSON updates) to keep frame times smooth.


## Testing strategy

- Device matrix across `armeabi-v7a`, `arm64-v8a`, `x86`, `x86_64`.
- Regression tests for:
  - Marker rendering and selection.
  - Tracks and waypoints visibility and styles.
  - Location component and permissions.
  - Clustering behavior at varying zooms.
  - Offline region create/purge flows (Phase 3).
- Manual checks for F-Droid build and install flow (“no Google” hygiene already present in the build).


## Timeline (estimate)

- Phase 1: 1–2 weeks (core rendering, location, parity for main screen)
- Phase 2: 1 week (clustering + polish)
- Phase 3: 2–3 weeks (offline regions + UX)
- Phase 4: 0.5–1 week (cleanup, remove osmdroid code)


## Open questions

- Preferred default production style: community vector tiles vs. raster OSM tiles?
- Confirm MapLibre Native offline APIs and best-practice for Android in the selected version.
- Retain any WMS layers? If needed, evaluate WMS via raster tile intermediary or custom source pipeline.


## References

- MapLibre Native repository and README (Android usage and examples): https://github.com/maplibre/maplibre-native
  - Shows `MapView` usage, style loading, and dependency coordinates in the README.
  - Recent Android releases (e.g., android-v12.1.0) are available in the releases section.

## Proof of Concept (POC) scope and steps

Objective: Stand up MapLibre Native in the F-Droid flavor alongside the existing osmdroid implementation without removing osmdroid yet. Demonstrate base map rendering, device location, and rendering of core app entities (nodes, waypoints, tracks) using MapLibre sources/layers. Keep the change additive and easy to revert.

Scope (must-have):
- Add MapLibre dependency to `fdroid` configuration.
- Introduce a new, isolated Composable (e.g., `MapLibreMapView`) using `AndroidView` to host `org.maplibre.android.maps.MapView`.
- Initialize MapLibre and load a dev-safe style (e.g., `https://demotiles.maplibre.org/style.json`).
- Render nodes and waypoints using `GeoJsonSource` + `SymbolLayer` with a simple, built-in marker image.
- Render tracks as polylines using `GeoJsonSource` + `LineLayer`.
- Enable the MapLibre location component (when permissions are granted).
- Provide a temporary developer entry point to reach the POC screen (e.g., a debug-only navigation route or an in-app dev menu item for `fdroidDebug` builds).

Scope (nice-to-have, if time allows):
- Simple style switcher between 2–3 known-good styles (dev/demo only).
- Add a “Custom raster URL” input to attach a `RasterSource` + `RasterLayer` using `{z}/{x}/{y}` templates.
- Basic cluster styling using a clustered `GeoJsonSource` (no cluster detail dialog yet).

Out of scope for POC:
- Offline region downloads and cache management UI.
- KML import and full custom layer manager (GeoJSON-only import is acceptable if trivial).
- Complete parity polish and advanced gestures/controls.

Implementation steps:
1) Build configuration
   - Add `fdroidImplementation("org.maplibre.gl:android-sdk:<latest>")`.
   - Keep osmdroid dependencies during POC; we will not remove them yet.

2) New POC Composable and lifecycle
   - Create `MapLibreMapView` in the `fdroid` source set.
   - Use `AndroidView` to host `MapView`; forward lifecycle events (onStart/onStop/etc.).
   - Call `MapLibre.getInstance(context)` and set the style URI once the map is ready.

3) Data plumbing
   - From the existing `MapViewModel`, derive FeatureCollections for nodes, waypoints, and tracks.
   - Create `GeoJsonSource` entries for each category; update them on state changes.
   - Add a default marker image to the style and wire a `SymbolLayer` for nodes/waypoints; add a `LineLayer` for tracks.

4) Location component
   - Enable MapLibre’s location component when location permission is granted.
   - Add a simple “my location” action (centers the camera on the device location).

5) Temporary navigation
   - Add a debug-only nav destination or dev menu entry to open the POC screen without impacting existing map flows.

6) Developer QA checklist
   - Build `assembleFdroidDebug` and open the POC map.
   - Verify: base map loads; device location shows when permitted; nodes/waypoints/track render.
   - Verify: panning/zooming works smoothly on at least one ARM64 device and one emulator.

Acceptance criteria:
- On `fdroidDebug`, a developer can open the POC screen and see:
  - A MapLibre-based map with a working style.
  - Device location indicator (when permission is granted).
  - Visible nodes, waypoints, and at least one track drawn via MapLibre layers.
- No regressions to existing osmdroid-based map screens.

Estimated effort: 1–2 days