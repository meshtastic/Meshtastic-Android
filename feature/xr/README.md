# `:feature:xr`

## Overview

The `:feature:xr` module holds the Android XR support on Jetpack XR (`androidx.xr.compose`, `scenecore`, `runtime`).

- `SpatialShell` wraps `MainScreen`. In Home Space it draws the app as is, plus a button at the bottom start of the window that enters Full Space. In Full Space it keeps the app as the main panel at its Home Space size and adds an orbiter below it to go back.
- Phones, tablets and desktop see no change: everything is gated on `LocalSpatialCapabilities` and `LocalSession`.

**Target: Android only, google flavor only** (not KMP — uses the `meshtastic.android.library` + `meshtastic.android.library.compose` convention plugins). Jetpack XR depends on ARCore (`com.google.ar:core`) and a prebuilt native renderer (`com.google.ar:impress`), so `androidApp` takes this module with `googleImplementation` and the fdroid flavor's `AppSpatialShell` is a pass-through.

## Key Rules

- Never size the main panel. `SpatialMainPanel` with an explicit size resizes the window, which recreates `MainActivity` and leaks the old one through the platform's XR panel listener.
- Spatial UI and 3D content show only in Full Space; gate on `LocalSpatialCapabilities`, never on device.
