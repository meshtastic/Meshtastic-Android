# `:feature:xr`

Android XR support on Jetpack XR. Today it holds the spatial shell: on a headset in Full Space the app sits in a movable, resizable panel with an orbiter below it, and in Home Space a button enters Full Space. On phones and tablets it draws the app unchanged.

**Target: Android only, google flavor only.** Jetpack XR depends on ARCore (`com.google.ar:core`) and a prebuilt native renderer (`com.google.ar:impress`), so `androidApp` takes this module with `googleImplementation` and the fdroid flavor's `AppSpatialShell` is a pass-through.
