# `:feature:xr`

## Overview

The `:feature:xr` module holds the Android XR support on Jetpack XR (`androidx.xr.compose`, `scenecore`, `runtime`).

- `SpatialShell` wraps `MainScreen`. In Home Space it draws the app as is, plus a button at the bottom start of the window that enters Full Space. In Full Space it keeps the app as the main panel at its Home Space size and adds an orbiter below it to go back.
- Phones, tablets and desktop see no change: everything is gated on `LocalSpatialCapabilities` and `LocalSession`.

**Target: Android only, google flavor only** (not KMP — uses the `meshtastic.android.library` + `meshtastic.android.library.compose` convention plugins). Jetpack XR depends on ARCore (`com.google.ar:core`) and a prebuilt native renderer (`com.google.ar:impress`), so `androidApp` takes this module with `googleImplementation` and the fdroid flavor's `AppSpatialShell` is a pass-through.

## Device model in your space

`spatialDeviceModelViewer` is the headset's `DeviceModelArViewer`: "View in your space" asks for Full Space and stands the model in the main panel's plane past its right edge, at real size. The flasher's models are in meters, so the only scaling is undoing the main panel's own scale. Off XR it delegates to the phone's ARCore viewer.

- The banana is a separate entity under the device, on its left, so it moves with the device and toggling it does not reload the device.
- The device's live screen is a panel on its display glass, fed by the mirror frames the phone views use. It is made at 512 px and scaled to the glass, and draws without bitmap smoothing.
- Entities are disposed before the models they borrow: the native side aborts if a model is released while an entity holds it.

## Key Rules

- Never size the main panel. `SpatialMainPanel` with an explicit size resizes the window, which recreates `MainActivity` and leaks the old one through the platform's XR panel listener.
- Spatial UI and 3D content show only in Full Space; gate on `LocalSpatialCapabilities`, never on device.
