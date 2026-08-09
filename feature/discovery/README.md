# `:feature:discovery`

## Overview

The `:feature:discovery` module implements **Local Mesh Discovery**: the app cycles the connected radio through a queue of LoRa modem presets, dwells on each for a configured duration while collecting packets, then persists and ranks the results so the user can see which preset (or beacon-advertised custom channel) has the most active mesh nearby. Sessions are stored via `DiscoveryDao` and can be revisited, mapped, summarised (AI or algorithmic), and exported.

**Targets:** Android · JVM (Desktop) · iOS (via `meshtastic.kmp.feature` convention plugin)

## Scan Workflow

- `DiscoveryScanEngine` — core scan engine. Cycles through a queue of `ScanTarget`s, dwells on each while collecting packets, and persists aggregated results via `DiscoveryDao`.
- `DiscoveryScanState` — state machine for the scan lifecycle: `Idle → Preparing → Shifting → [Reconnecting] → Dwell → … → Analysis → Complete`, with `Cancelling`/`Restoring`/`Failed`/`Paused` side paths.
- `ScanTarget` — one queue entry. `channel == null` is a public-preset target; a non-null channel is a beacon-advertised custom channel: the engine tunes the radio's primary channel to that name+PSK (and region) for the dwell, then restores it.
- `DiscoveryRankingEngine` (`scan/`) — ranks preset results by unique node count, neighbor diversity, non-duplicate packet count, and SNR.
- `Check24GhzCapability` (`scan/`) — layered heuristic that determines whether the connected radio supports 2.4 GHz LoRa (SX1280), returning `Supported` / `Unsupported` / `Unknown`.

## ViewModels & Screens

| ViewModel | Screen (`ui/`) | Purpose |
|---|---|---|
| `DiscoveryViewModel` | `DiscoveryScanScreen` | Scan setup and live progress (drives `DiscoveryScanEngine`) |
| `DiscoverySummaryViewModel` | `DiscoverySummaryScreen` | Post-scan session summary, ranking, and export |
| `DiscoveryMapViewModel` | `DiscoveryMapScreen` | Discovered-node map with per-preset filter chips (rendered via `LocalDiscoveryMapProvider` from `core:ui`) |
| `DiscoveryHistoryViewModel` | `DiscoveryHistoryScreen` | Past session list with delete |
| `DiscoveryHistoryDetailViewModel` | `DiscoveryHistoryDetailScreen` | Detail view of one stored session |

Navigation entries are registered by `discoveryGraph()` in `navigation/DiscoveryNavigation.kt` (Navigation 3, `DiscoveryRoute.*` keys).

## Summary Layer: AI vs Algorithmic

Same Google-flavor Gemini Nano vs fallback split as `:feature:docs`:

- `ai/DiscoverySummaryAiProvider` — interface for natural-language session/preset summaries.
- `ai/AlgorithmicSummaryProvider` — deterministic fallback delegating to `DiscoverySummaryGenerator`. Registered with `binds = []`; each platform binds the interface explicitly: the Android `google` flavor binds `GeminiNanoSummaryProvider` (in `:androidApp`), while `fdroid`, Desktop, and iOS bind the algorithmic provider.
- `ai/LoRaPresetReference` — modem-preset reference data (bandwidth, spreading factor, link budget) used to enrich AI prompts and algorithmic summaries.

## Export

Multiplatform export path in `export/`:

- `DiscoveryExporter` — interface: `export(DiscoveryExportData): ExportResult` (success = bytes + MIME type + filename).
- `DiscoveryReportFormatter` — shared formatting of session/preset report lines and filenames.
- `rememberExportSaver()` / `ExportSaverLauncher` — `expect`/`actual` file-save seam per platform (`ExportSaver.android.kt` → SAF document picker, `ExportSaver.jvm.kt` → file dialog, `ExportSaver.ios.kt`).
- `PdfDiscoveryExporter` (androidMain) renders a PDF report; `TextDiscoveryExporter` (jvmMain) renders plain text.

## Mesh Beacon Invitations

UI for port-37 Mesh Beacon join invitations (`ui/component/`):

- `MeshBeaconInvitationCard` — a received beacon offer with its advertised channel/region/preset; the user can survey the mesh first, join it, or dismiss. Shown on `DiscoveryScanScreen`.
- `BeaconChannelsCard` — scan-setup section listing distinct beacon-advertised custom channels; selecting a row adds a custom-channel `ScanTarget`.

## Key Dependencies

From `feature/discovery/build.gradle.kts` (`commonMain`):

- `core:common`, `core:data`, `core:database`, `core:di`, `core:model`, `core:navigation`, `core:network`, `core:prefs`, `core:repository`, `core:resources`, `core:service`, `core:ui`
- `kotlinx.collections.immutable`
- `org.meshtastic:protobufs` (Maven artifact)
