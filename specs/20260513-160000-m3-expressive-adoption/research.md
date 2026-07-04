# Research: M3 Expressive Design System Adoption

**Date**: 2025-07-18 | **Branch**: `20260513-160000-m3-expressive-adoption`

## Research Questions & Findings

### RQ-1: What expressive typography APIs does CMP material3 1.11.0-alpha07 provide?

**Decision**: Use `MaterialTheme.typography` emphasized extension properties available in `ExperimentalMaterial3ExpressiveApi`.

**Rationale**: CMP material3 1.11.0-alpha07 (backed by Jetpack Compose Material3 ~1.5.0-alpha) exposes `Typography` with emphasized variants accessible via `MaterialTheme.typography.displayLargeEmphasized`, `headlineMediumEmphasized`, `titleMediumEmphasized`, `bodyLargeEmphasized`, and `labelLargeEmphasized`. These are extension properties gated behind `@ExperimentalMaterial3ExpressiveApi`. The Typography constructor itself does not accept emphasized parameters directly — instead, the expressive theme auto-generates them based on the standard typescale using bolder font weights and slightly adjusted letter spacing.

**Alternatives Considered**:
- Manually constructing emphasized `TextStyle` variants → Rejected per constraint: no manual reimplementations of CMP APIs
- Bundling Roboto Flex variable font → Rejected per constraint: accept degradation pre-Android 12

**Pre-Android 12 Behavior**: Without Roboto Flex, the system falls back to standard Roboto weight variants (400/500/700). Emphasized styles degrade to the nearest available weight. No visual breakage, just less granular weight expression.

---

### RQ-2: What expressive component variants are available in CMP material3 1.11.0-alpha07?

**Decision**: Use only officially provided expressive APIs; defer unavailable ones.

**Available (confirmed in 1.11.0-alpha07)**:
| Component | API | Notes |
|-----------|-----|-------|
| Theme | `MaterialExpressiveTheme` | ✅ Already in use |
| Motion | `MotionScheme.expressive()` | ✅ Already in use |
| FAB | `FloatingActionButtonDefaults.shape` / morphing | ✅ Expressive shapes available |
| Progress | `CircularWavyProgressIndicator`, `LinearWavyProgressIndicator` | ✅ Wavy variants available |
| Navigation Bar | `NavigationBarItemDefaults` expressive pill indicator | ✅ Available |
| Navigation Rail | `NavigationRailItemDefaults` expressive pill indicator | ✅ Available |
| Button | `ButtonDefaults` with expressive shapes | ✅ Round shapes available |
| Slider | No explicit expressive variant | ⚠️ Use standard Slider with spring animation manually |
| SwipeToDismissBox | `SwipeToDismissBoxState`, `SwipeToDismissBox` | ✅ Foundation API available |
| Tooltip | `PlainTooltip`, `RichTooltip`, `TooltipBox` | ✅ Available |
| Top App Bar | `TopAppBarDefaults` with expressive styling | ✅ Available |

**Not Available (deferred per NFR-007)**:
| Component | Status |
|-----------|--------|
| Expressive focus rings (animated) | Not in CMP material3 yet — use `Modifier.focusable()` with custom indication |
| Shape morphing on press for FABs | Partial — `InteractionSource`-based shape is experimental |
| Expressive Slider | No distinct expressive variant; use spring animateFloatAsState for thumb |

**Rationale**: Per spec NFR-007 and clarification, we only use what's officially available. Animated focus rings and shape-morph-on-press are deferred until CMP ships them. Standard focus indication + spring-animated state changes provide acceptable intermediate UX.

---

### RQ-3: How to implement swipe-to-action on Compose Multiplatform lists?

**Decision**: Use Compose Foundation `AnchoredDraggableState` + custom `SwipeToRevealBox` composable in `core/ui`.

**Rationale**: `SwipeToDismissBox` from Material3 is designed for dismiss (delete) semantics. For reveal-actions (swipe to show action buttons without dismissing), the recommended pattern is:
1. Use `AnchoredDraggableState` from `androidx.compose.foundation.gestures`
2. Create anchors at rest (0), partial reveal (action width), and optionally full-swipe (action + execute)
3. Render action content behind the foreground item using `Box` with offset

This approach:
- Works in `commonMain` (no platform-specific code needed)
- Allows bi-directional swipe (left = mute, right = request position)
- Supports spring animations via `AnchoredDraggableState.animateTo()` with spring spec
- Is composable and reusable across node list and message list

**Alternatives Considered**:
- `SwipeToDismissBox` → Only supports dismiss semantics, not reveal-and-hold
- Third-party swipe library → Violates principle of minimal dependencies; foundation API is sufficient
- Custom `Modifier.pointerInput` → Lower-level; `AnchoredDraggable` handles fling velocity, snapping, and state persistence

---

### RQ-4: How should the swipe discoverability hint work?

**Decision**: Animate a 24dp edge-peek on the first visible list item using `LaunchedEffect` with a spring, controlled by a DataStore boolean `hasCompletedSwipeAction`.

**Rationale**: The spec requires "hint repeats each session until user completes a successful swipe action." This means:
- Store `hasCompletedSwipeAction: Boolean` in user preferences (DataStore)
- On first composition of node/message list screen, if `!hasCompletedSwipeAction`, run a one-shot animation that offsets the first item 24dp to reveal a sliver of the action background
- After 1.0 seconds hold, spring back to rest position (400ms return)
- Once the user successfully completes any swipe action, set `hasCompletedSwipeAction = true` and never show hint again

**Session vs. Permanent**: Per clarification, hint "repeats each session." Interpretation: a "session" = app process lifecycle. Use an in-memory flag (ViewModel-scoped `MutableStateFlow<Boolean>`) that resets on process death. DataStore persists the "has ever swiped" permanent dismissal. The per-session hint shows if the permanent flag is false.

---

### RQ-5: How to apply spring-physics navigation indicator animations?

**Decision**: The expressive `MotionScheme` already applies spring physics to navigation indicator movement when using `NavigationBar`/`NavigationRail` with `MaterialExpressiveTheme`. No additional configuration needed beyond ensuring the theme wraps all navigation composables.

**Rationale**: `MotionScheme.expressive()` overrides the default animation specs for all Material3 component internal animations. The navigation indicator (pill shape) animation between items uses the motion scheme's `defaultSpatialSpec` which is a spring with `stiffness = 300f` and `dampingRatio = 0.7f`. This is automatic when the composable is inside `MaterialExpressiveTheme`.

**Verification**: Can be confirmed by running the app in Layout Inspector and observing the indicator transition duration/curve. Should complete in ~250-350ms with slight overshoot.

---

### RQ-6: How to handle `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` at scale?

**Decision**: Apply file-level `@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)` in `core/ui/theme/Theme.kt` and `core/ui/theme/Type.kt`. For feature modules, use function-level `@OptIn` only on composables that directly call expressive APIs.

**Rationale**: 
- File-level in theme files is acceptable because the entire theme is fundamentally expressive
- Function-level in feature modules maintains visibility of which specific composables depend on experimental APIs
- This enables easy grep/search for expressive API usage when evaluating migration path as APIs stabilize
- Detekt/lint won't flag OptIn propagation issues with this approach

---

### RQ-7: Screenshot test strategy for visual regression during migration?

**Decision**: Update existing screenshot tests incrementally per user story. Each PR that changes component appearance must update corresponding reference images.

**Rationale**: The project uses `compose-screenshot 0.0.1-alpha14` with multi-variant test setup. Strategy:
1. Before each component change, confirm current screenshot tests pass (baseline)
2. After applying expressive styling, run `./gradlew updateScreenshotTests` to capture new references
3. Include updated reference PNGs in the same PR as the styling change
4. Review visual diff in PR to confirm intentional changes only

This is straightforward incremental migration — no special infrastructure needed.

---

### RQ-8: Cross-platform spec requirement from Constitution §V?

**Decision**: Mark as N/A — M3 Expressive is platform-specific (Android/Compose).

**Rationale**: Per the spec's `Cross-Platform Spec` field: "N/A — platform-specific only. M3 Expressive APIs are Android/Compose-specific; desktop and iOS targets will receive equivalent improvements via CMP material3 as those APIs stabilize." Additionally, the `meshtastic/design` repo has no `features/` directory (404) and no mention of "expressive" anywhere. The design standards v1.4 do not prescribe animation/motion guidance, leaving this to platform implementations.

Constitution §V is satisfied because:
1. The feature does not change cross-platform behavior semantics
2. It's an implementation-layer styling upgrade, not a UX flow change
3. Desktop/iOS get the styling automatically through `commonMain` when CMP stabilizes the APIs
