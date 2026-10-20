# `:feature:widget`

## Overview

The `:feature:widget` module holds the **Android-only Jetpack Glance home-screen widgets** (API 26+):

- `LocalStatsWidget` shows live mesh statistics for the connected node.
- `NodeTrackerWidget` follows one node the user picks: battery, signal, last heard, hops away and distance from our node.

**Target: Android only** (not KMP — uses `meshtastic.android.library` + `meshtastic.android.library.compose` convention plugins)

## Key Responsibilities

- Display live mesh statistics and a tracked node on the Android home screen via Jetpack Glance
- Combine four reactive data sources into a single `StateFlow<LocalStatsWidgetUiState>` for efficient re-renders
- Pick the tracked node in `NodeTrackerConfigActivity`, the widget's configuration activity, and store it per instance in Glance state
- Fulfill the `AppWidgetUpdater` interface from `:core:repository` so the mesh service can trigger widget refreshes without depending on Android widget APIs

## Source Structure

```text
src/main/kotlin/org/meshtastic/feature/widget/
├── LocalStatsWidget.kt              ← GlanceAppWidget, 3 responsive sizes
├── LocalStatsWidgetReceiver.kt      ← GlanceAppWidgetReceiver
├── LocalStatsWidgetState.kt         ← LocalStatsWidgetUiState + StateProvider
├── NodeTrackerWidget.kt             ← GlanceAppWidget, 2 responsive sizes
├── NodeTrackerWidgetReceiver.kt     ← GlanceAppWidgetReceiver
├── NodeTrackerWidgetState.kt        ← NodeTrackerWidgetState, nodeTrackerState(), DataSource
├── NodeTrackerConfigActivity.kt     ← APPWIDGET_CONFIGURE node picker (Compose, Material 3)
├── NodeTrackerCandidates.kt         ← picker filter and order
├── WidgetNodeChip.kt                ← node chip shared by both widgets
├── AndroidAppWidgetUpdater.kt       ← implements AppWidgetUpdater (core:repository)
├── RefreshLocalStatsAction.kt       ← ActionCallback (refresh button)
├── WidgetResourceEnvironment.kt     ← bridges Compose resources into Glance
└── di/
    └── FeatureWidgetModule.kt
```

## Node Tracker

Each instance stores its node number under `TRACKED_NODE_NUM_KEY` in its Glance Preferences state, and the last reading it rendered from a node DB under the `last_known_*` keys, which it shows dimmed while no node DB is open. `NodeTrackerConfigActivity` writes it, from the launcher's `APPWIDGET_CONFIGURE` on placement or reconfiguration (`widgetFeatures="reconfigurable"`), or from the widget's own "Node not found" state. The activity is exported for the launcher, so it honors only widget ids bound to `NodeTrackerWidgetReceiver`, and it returns `RESULT_CANCELED` unless a node is picked.

| Size | Data shown |
|---|---|
| Small (under 200 dp wide) | Node chip, long name, battery, last heard |
| Wide | Adds SNR and RSSI (direct, non-MQTT nodes only), hops away, distance (only while connected) |

`nodeTrackerState()` maps the node DB to what one instance renders. A node not heard within the app's online window renders dimmed; the provider's 30-minute `updatePeriodMillis` re-renders a node that has gone quiet. Tapping opens the node's details through the `nodes/<num>` deep link. The picker lists favorites first, then the most recently heard, leaving out our own node and ignored nodes.

The widget holds no lazy collection, which the `ActionTrampolineActivity` removal in `androidApp/src/main/AndroidManifest.xml` relies on.

## Local Stats Content

The widget shows the following information when connected:

| Section | Data shown |
|---|---|
| Node identity | Short name chip with node colors |
| Battery | Level percentage + progress bar |
| Channel utilization | Percentage + progress bar |
| Air utilization | Percentage + progress bar |
| Traffic | TX / RX packet counts, duplicates, relay TX, relay canceled, dropped, bad RX |
| Diagnostics | Noise floor (dBm), free heap / total heap |
| Footer | Online nodes / total nodes, device uptime, last-updated timestamp |

When not connected, the widget shows the app icon (a progress spinner while connecting) and the connection status: **Disconnected**, **Connecting**, or **Device sleeping**.

## Key Types

### `LocalStatsWidgetUiState`

```kotlin
data class LocalStatsWidgetUiState(
    val connectionState: ConnectionState,
    val isConnecting: Boolean,
    val showContent: Boolean,
    // Node identity
    val nodeShortName: String?,
    val nodeColors: Pair<Int, Int>?,
    // Battery
    val batteryLevel: Int,
    val hasBattery: Boolean,
    val batteryProgress: Float,
    // Utilization
    val channelUtilization: Float, val channelUtilizationProgress: Float,
    val airUtilization: Float, val airUtilizationProgress: Float,
    // Traffic counters
    val hasStats: Boolean,
    val numPacketsTx: Int, val numPacketsRx: Int,
    val numRxDupe: Int, val numTxRelay: Int, val numTxRelayCanceled: Int,
    val numTxDropped: Int, val numPacketsRxBad: Int,
    // Diagnostics
    val noiseFloor: Int,
    val heapFreeBytes: Int, val heapTotalBytes: Int,
    // Footer
    val totalNodes: Int, val onlineNodes: Int,
    val uptimeSecs: Long,
    val updateTimeMillis: Long,
)
```

Every field has a default in the source; the listing leaves them out. Until the node has sent `LocalStats` (`hasStats` false), utilization and uptime fall back to the node's own device metrics.

### `LocalStatsWidgetStateProvider`

Koin `@Single` that eagerly combines four reactive flows (`ConnectionStateProvider` is injected as the constructor param `connectionStateProvider`):
- `ConnectionStateProvider.connectionState`
- `NodeRepository.nodeDBbyNum` (online/total counts)
- `NodeRepository.localStats`
- `NodeRepository.ourNodeInfo`

```kotlin
val state: StateFlow<LocalStatsWidgetUiState>
```

### `LocalStatsWidget`

```kotlin
class LocalStatsWidget : GlanceAppWidget(), KoinComponent {
    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(100.dp, 100.dp), DpSize(250.dp, 100.dp), DpSize(250.dp, 250.dp))
    )
}
```

The sizes are `SMALL_SQUARE`, `HORIZONTAL_RECTANGLE` and `BIG_SQUARE`. Tapping the widget opens the app. The refresh button fires `RefreshLocalStatsAction`, which asks the local node for fresh `LOCAL_STATS` and `DEVICE` telemetry through `CommandSender`; the new values then reach the widget through the state flow. A rejected or unanswerable request is logged and skipped.

Glance doesn't provide the Compose UI locals that Compose Multiplatform's `stringResource` reads, so `WidgetResourceEnvironment` bridges them into the widget's composition; every translated string in the widget depends on it. A composition failure shows the `widget_composition_error` layout through `onCompositionError` instead of crashing the app.

## `AppWidgetUpdater` integration

The mesh service holds a reference to `AppWidgetUpdater` (defined in `:core:repository`) without depending on Android widget APIs. `AndroidAppWidgetUpdater` in this module provides the concrete implementation:

```kotlin
// Simplified — the real update wraps each call in a try/catch
@Single
class AndroidAppWidgetUpdater(
    private val context: Context,
    stateProvider: LocalStatsWidgetStateProvider,
    trackerDataSource: NodeTrackerWidgetDataSource,
) : AppWidgetUpdater {
    override suspend fun updateAll() {
        LocalStatsWidget().updateAll(context)
        NodeTrackerWidget().updateAll(context)
    }
}
```

It also drives re-renders itself, because Glance compositions are ephemeral:

- Local Stats: observes `stateProvider.state` (debounced 500 ms, ignoring timestamp-only changes) while instances exist.
- Node Tracker: samples `trackerDataSource.inputs` once a second, maps it to each instance's rendered state, and updates only when one of those changes. Sampling rather than debouncing keeps a busy mesh from starving the update.

This keeps the service layer platform-agnostic while still enabling widget refresh on data changes.

## Dependency Graph

### Key Dependencies

```text
feature:widget (Android only)
  ├── core:common, core:model, core:navigation, core:resources, core:repository, core:ui
  ├── androidx.glance.appwidget, glance.material3, glance.preview, glance.appwidget.preview
  ├── androidx.activity.compose, compose.multiplatform.material3 (NodeTrackerConfigActivity)
  ├── compose.multiplatform.ui (LocalConfiguration, LocalDensity), compose.multiplatform.resources
  └── kermit
```

## Testing

This is a plain Android library, so its unit tests run under `./gradlew :feature:widget:testDebugUnitTest` (or the root `test` task), with Robolectric and Glance's testing artifacts.

<!--region graph-->
```mermaid
graph TB
  :feature:widget[widget]:::android-feature
  :feature:widget -.-> :core:common
  :feature:widget -.-> :core:model
  :feature:widget -.-> :core:navigation
  :feature:widget -.-> :core:resources
  :feature:widget -.-> :core:repository
  :feature:widget -.-> :core:ui

classDef android-application fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef android-application-compose fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef compose-desktop-application fill:#CAFFBF,stroke:#000,stroke-width:2px,color:#000;
classDef android-feature fill:#FFD6A5,stroke:#000,stroke-width:2px,color:#000;
classDef android-library fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
classDef android-library-compose fill:#9BF6FF,stroke:#000,stroke-width:2px,color:#000;
classDef android-test fill:#A0C4FF,stroke:#000,stroke-width:2px,color:#000;
classDef jvm-library fill:#BDB2FF,stroke:#000,stroke-width:2px,color:#000;
classDef kmp-feature fill:#FFD6A5,stroke:#000,stroke-width:2px,color:#000;
classDef kmp-library-compose fill:#FFC1CC,stroke:#000,stroke-width:2px,color:#000;
classDef kmp-library fill:#FFC1CC,stroke:#000,stroke-width:2px,color:#000;
classDef unknown fill:#FFADAD,stroke:#000,stroke-width:2px,color:#000;

```
<!--endregion-->
