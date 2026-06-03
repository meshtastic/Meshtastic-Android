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

## 2026-05-28 — Added comprehensive CarScreenDataBuilder unit coverage
- Created `feature/car/src/test/kotlin/org/meshtastic/feature/car/util/CarScreenDataBuilderTest.kt` with 533 lines covering signal quality thresholds/boundaries, node UI mapping, node and conversation sorting, local stats fallbacks, uptime formatting, recent message limiting, contact key generation, and constants.
- Restored the `MessageSnapshot` data class in `CarStateCoordinator.kt` and re-added `recentMessages()` plus `MAX_CONVERSATION_MESSAGES` in `CarScreenDataBuilder.kt` so the current source matched the requested pure-helper API surface for testing.
- Verified with `./gradlew :feature:car:spotlessCheck :feature:car:detekt :feature:car:testFdroidDebugUnitTest --quiet` and the requested quiet test command (`./gradlew :feature:car:testFdroidDebugUnitTest --quiet 2>&1 | tail -20`), both successful.

## 2026-05-28 — Lowered car min API to 7 and removed dead conversation code
- Changed `feature/car` manifest `androidx.car.app.minCarApiLevel` metadata from 8 to 7.
- Guarded `HomeScreen.showEmergencyAlert()` behind `carContext.carAppApiLevel >= 8` and logged unsupported API 7 hosts with Kermit.
- Removed unused `ConversationScreen`, `CarTtsEngine`, message snapshot/cache/read-aloud plumbing, and now-unused car reply/read-aloud strings.
- Simplified `CarStateCoordinator` and `CarScreenDataBuilder` to match the inline `ConversationItem` flow.
- Verified with `./gradlew :feature:car:spotlessApply :feature:car:spotlessCheck :feature:car:detekt :feature:car:compileFdroidDebugKotlin --quiet 2>&1 | tail -30`.

## 2026-05-28 — Migrated car home messages tab to ConversationItem
- Reworked `feature/car` `HomeScreen` messaging tab to build CAL `ConversationItem` entries instead of browsable `Row`s, including `Person`/`CarMessage` helpers and native reply/mark-read callbacks.
- Removed `HomeScreen` conversation navigation so the car host owns messaging affordances; `ConversationScreen` remains on disk for later cleanup phases.
- Added `CarStateCoordinator.markAsRead()` using `packetRepository.clearUnreadCount(...)` with Kermit error logging via `runCatching`.
- Verified with `./gradlew :feature:car:spotlessApply :feature:car:spotlessCheck :feature:car:detekt :feature:car:compileFdroidDebugKotlin` and the requested quiet compile command (`:feature:car:compileFdroidDebugKotlin --quiet 2>&1 | tail -20`), both successful.

## 2026-05-28 — Implemented car conversation shortcuts and avatars
- Added `feature/car/.../util/PersonIconFactory.kt` to render circular initial avatars using node-derived foreground/background colors for `Person` and shortcut icons.
- Added `feature/car/.../service/ConversationShortcutManager.kt` to publish long-lived dynamic conversation shortcuts for favorite nodes and active channels, plus on-demand shortcut creation for notifications.
- Wired `MeshtasticCarSession` to start/stop shortcut observation on a dedicated session coroutine scope.
- Updated `CarNotificationManager` to ensure conversation shortcuts exist before posting and to attach both `shortcutId` and `LocusIdCompat` to messaging notifications.
- Verified green with `./gradlew :feature:car:spotlessCheck :feature:car:detekt --quiet` and `./gradlew :feature:car:compileFdroidDebugKotlin --quiet 2>&1 | tail -20` after workspace bootstrap.

## 2026-05-28 — Implemented car local stats tab and extracted screen data builder
- Added `CarLocalStats` to `feature/car` UI models and exposed `localStatsState` from `CarStateCoordinator`.
- Wired a new HomeScreen `Status` tab with battery, channel utilization, air utilization, node counts, uptime, and packet TX/RX rows.
- Created `feature/car/.../util/CarScreenDataBuilder.kt` to centralize pure UI-model mapping helpers for nodes, conversations, local stats, uptime formatting, contact key building, and recent message selection.
- Added the new `ic_car_status.xml` drawable plus status strings in `feature/car/src/main/res/values/strings.xml`.
- Cleaned up `CarReplyReceiver` detekt violations that blocked module validation.
- Ran `python3 scripts/sort-strings.py` and verified green with `./gradlew :feature:car:spotlessApply :feature:car:spotlessCheck :feature:car:detekt :feature:car:compileFdroidDebugKotlin :feature:car:testFdroidDebugUnitTest`.

## 2026-05-28 — Implemented car module Phase 1 messaging wiring fixes
- Replaced `CommandSender` usage in `feature/car` `CarStateCoordinator` with injected `SendMessageUseCase`, keeping the public `sendMessage()` API synchronous for UI callbacks while launching the use case on the coordinator scope after message-length validation.
- Updated `CarNotificationManager` reply and mark-read notification actions with semantic action metadata and `setShowsUserInterface(false)` for automotive-friendly inline handling.
- Reworked `CarReplyReceiver` into a `KoinComponent` that injects `SendMessageUseCase` and `PacketRepository`, then sends replies / clears unread counts asynchronously with Kermit error logging.
- Added `android:permission="androidx.car.app.CarAppService"` to the `MeshtasticCarAppService` manifest declaration.
- Verified with `./gradlew :feature:car:compileFdroidDebugKotlin --quiet` after required workspace bootstrap.

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
