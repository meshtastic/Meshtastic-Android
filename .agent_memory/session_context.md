# Agent Session Context - Meshtastic Android
# Dated handover log. Add new entries at the TOP. Format: ## YYYY-MM-DD — <summary>
#
# Capped at the ~5 most recent entries — skim the top entry for current state; you do
# not need to read the whole file. When adding an entry pushes the count past ~5, move
# the oldest entries to `session_context.archive.md` (not read by default). The
# "Golden Context" block at the bottom is stable across sessions; keep it here.

## 2026-06-03 — Cluster-marker FATAL: revert shipped map series + in-scope rememberComposeBitmapDescriptor fix
- Reverted ALL google-flavor map changes to before #5684 (per user): restored MapView.kt, NodeClusterMarkers.kt, WaypointMarkers.kt, InlineMap.kt to parent commit bc9f1637; deleted MarkerBitmapRenderer.kt; re-pinned `play-services-maps = 20.0.0` in libs.versions.toml. The shipped #5702–#5719 series (Canvas markers + ViewTree-owner band-aids) had lost the info-window popups + interactions.
- Root cause (verified against maps-compose 8.3.0 + android-maps-utils 4.1.1 SOURCE in gradle cache): ONLY `Clustering(clusterItemContent=…)` crashes — its `ComposeUiClusterRenderer` builds a *detached* `InvalidatingComposeView` with a fake lifecycle owner and NO SavedStateRegistryOwner. `MarkerComposable` already bakes its icon via the safe in-scope `rememberComposeBitmapDescriptor`; info windows render with the live marker compositionContext. So InlineMap/NodeTrack/Traceroute were left untouched.
- Fix (NodeClusterMarkers.kt ONLY): icons baked in-scope via `rememberComposeBitmapDescriptor(node){ PulsingNodeChip }` into a snapshot stateMap; custom `private class NodeClusterRenderer : DefaultClusterRenderer` assigns them in onBeforeClusterItemRendered/onClusterItemUpdated (bg thread, READ-only — never composes, so the crash class is gone). Native info windows (super sets title/snippet) + onClusterItemInfoWindowClick→navigateToNodeDetails; precision circles drawn from the renderer's own `unclusteredItems` MutableState (clusterItemDecoration can't fire — `ClusterRendererItemState` is lib-internal). Strictly better than the elegant-euler Canvas branch — keeps the REAL Compose chip.
- `compileGoogleDebugKotlin` + `spotlessCheck` + `detekt` PASS. NOT committed, NOT device-verified. Next: device-test (clusters show chips + info-window popups + no FATAL), then commit/push.

## 2026-05-28 — Stabilized DatabaseManager withDb retry host test
- Hardened `DatabaseManagerWithDbRetryTest` to remove CI race conditions by running the manager on a `StandardTestDispatcher(testScheduler)` instead of real `Dispatchers.IO`.
- Added a `withTimeout(10_000)` guard around the test body to fail fast on coordination stalls instead of hanging/flapping.
- Kept the deterministic retry trigger (`error("Connection pool is closed")`) and retained assertions that first attempt uses old DB and retry uses current DB.
- Made teardown resilient with `if (::manager.isInitialized) manager.close()` so setup/early failures do not cascade into teardown crashes.
- Verified with `:core:database:jvmTest --tests "org.meshtastic.core.database.DatabaseManagerWithDbRetryTest*"` and repeated it 5 consecutive runs without failures; `:core:database:detekt` also passed.

## 2026-05-21 — Upgraded Chirpy to a fully-personalized Live Diagnostic Node & Mesh Assistant
- Integrated `NodeRepository` into `GeminiNanoDocAssistant.kt` and the Google AI Koin dependency injection module (`GoogleAiModule.kt`).
- Developed a dynamic live-state prompt formatting block within `buildPrompt(...)` that queries current hardware model, firmware version, connection status, GPS capability, channel utilization, airtime, battery level/voltage, user profile long/short names, and total registered mesh peer counts & active online peers directly from `NodeRepository`'s reactive flows.
- Injected this live radio diagnostics context dynamically as a system instruction metadata block on every user query. This empowers the on-device model to answer real-time, personalized diagnostic questions (e.g. "what is my battery level?", "how many active nodes are on my mesh right now?") with 100% on-device offline accuracy.
- Tuned context retrieval constraints for the modern `nano-v4-full` (Gemini Nano v4) model: expanded the total context budget `MAX_CONTEXT_CHARS` from 8,000 to **32,000 characters** (up to ~12K tokens out of the model's native 32K window), and scaled `MAX_PAGE_CHARS` to **16,000 characters** and `MAX_SNIPPET_CHARS` to **8,000 characters** to supply vastly richer, more detailed, and complete documentation fragments.

## 2026-05-21 — Activated full on-device token streaming and polished Chirpy's personality instructions
- Upgraded the on-device inference flow inside `GeminiNanoDocAssistant.kt` to use Firebase AI SDK's reactive `generateContentStream(prompt)` instead of the blocking `generateContent` invocation.
- Aggregated chunks and emitted incremental `AIDocAssistantResult.Partial` states down the Kotlin Flow, enabling true word-by-word/chunk-by-chunk streaming in the UI for a much more responsive user experience.
- Refined the `SYSTEM_INSTRUCTION` personality rules for Chirpy to position him as our adorable LoRa radio Node mascot instead of an avian theme, emphasizing high-enthusiasm mesh networking, signal connectivity, battery status, and radio/routing concepts while preserving technical precision.
- Overhauled system error messages inside `DocsNavigation.kt` and the loading bubble state inside `ChirpyAssistantSheet.kt` to align with the mascot theme.

## 2026-05-21 — Implemented streaming chat support and Firebase Remote Config integration for Chirpy
- Added `firebase-config` dependency to Version Catalog `libs.versions.toml` and `androidApp/build.gradle.kts`.
- Added the `AIDocAssistantResult.Partial` variant to support intermediate stream updates.
- Extended the `AIDocAssistant` interface and implemented `answerStream` in both `KeywordFallbackAssistant` and `GeminiNanoDocAssistant`.
- Integrated Firebase Remote Config into `GeminiNanoDocAssistant` to dynamically fetch the model name (`chirpy_model_name`) and system instruction (`chirpy_system_instruction`) with release-optimized fetch intervals.
- Refactored `GeminiNanoDocAssistant.answer` to reuse `answerStream` flow under the hood, eliminating duplicate prompting code.
- Verified that all unit tests (`:feature:docs:allTests`) and static analysis checks (`spotlessApply spotlessCheck detekt`) pass 100% green.

## Golden Context (stable across sessions)
- Always check `.skills/compose-ui/strings-index.txt` before reading `strings.xml`.
- Run `python3 scripts/sort-strings.py` after adding strings to keep the index organized.
- Always check `gh run list` before pushing.
- Pre-commit hook `scripts/ai-guardrail.sh` protects against binary leaks (see script for install).

<!-- Older entries archived in session_context.archive.md -->
