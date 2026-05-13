# Research — Node List Layout

## R-001: Preference storage strategy for layout toggles

### Decision

Store all layout preferences in DataStore via `core:prefs`, using the existing `UiPrefsImpl` pattern. Each toggle is a `Boolean` preference exposed as a `StateFlow`.

### Rationale

- The codebase already uses DataStore for similar UI preferences (BLE scan prefs, node filter options, sort order).
- DataStore flows integrate naturally with Compose `collectAsState()` for zero-delay UI updates.
- Using a single DataStore instance per preference scope avoids database overhead for simple toggle state.

### Alternatives considered

- **Room table for layout preferences**: Rejected — too heavy for 10 boolean keys. Room is appropriate for entity data, not UI presentation state.
- **SharedPreferences wrapper**: Rejected — DataStore is the project standard and provides Flow-based observation.
- **In-memory-only state**: Rejected — preferences must persist across app restarts (FR-002, FR-004).

### Consequences

- All 10 preference keys must be defined as constants to prevent key string drift (NFR-003).
- Eagerly-seeded `StateFlow` with defaults ensures the UI never flickers during DataStore cold reads.

---

## R-002: Compact row composable architecture

### Decision

Create `NodeItemCompact` as a standalone composable, separate from the existing `NodeItem`. Do not make `NodeItem` configurable — keep it as the unconditional Complete layout.

### Rationale

- The Complete and Compact layouts have fundamentally different structures (fixed rows vs toggle-driven rows, static circle vs adaptive circle).
- Sharing a single composable with toggle parameters would create a complex conditional tree that is harder to maintain and test.
- Two separate composables allow independent optimization (e.g., Compact can skip measuring rows that are hidden).

### Alternatives considered

- **Single `NodeItem` with a `compact: Boolean` parameter**: Rejected — leads to deeply nested conditionals and makes each layout harder to reason about independently.
- **Compose `AnimatedContent` switching between layouts**: Considered for the transition, but the actual row rendering should be separate composables. `AnimatedContent` or `Crossfade` can wrap the delegation in `NodeListScreen`.

### Consequences

- Both `NodeItem` and `NodeItemCompact` must independently implement TalkBack semantics.
- Shared sub-components (`NodeChip`, `MaterialBatteryInfo`, `LastHeardInfo`, etc.) remain in `core:ui` and are composed by both layouts.

---

## R-003: Adaptive chip sizing formula

### Decision

Use `max(36.dp, min(70.dp, 24.dp × lineCount))` where `lineCount` is the number of active row groups (1–3).

### Rationale

- The formula produces three discrete sizes: 36.dp (1 row), 48.dp (2 rows), 70.dp (3 rows = same as Complete).
- The minimum 36.dp ensures the short name remains readable even when all optional rows are hidden.
- The maximum 70.dp matches the Complete layout's fixed chip, maintaining visual consistency when all rows are enabled.
- 24.dp per line provides proportional vertical alignment between the chip and the content column.

### Alternatives considered

- **Fixed chip size regardless of toggles**: Rejected — wastes vertical space when optional rows are hidden, reducing the density benefit.
- **Continuous scaling based on total row height**: Rejected — overly complex and produces non-standard sizes that feel inconsistent.
- **Collapsing to a capsule/pill shape at minimum**: Rejected — the `NodeChip` maintains consistent card styling at all sizes (FR-010).

### Consequences

- The `lineCount` computation must be derived from toggle state, not from actual data presence. This ensures consistent sizing across all nodes regardless of their individual data completeness.

---

## R-004: Signal quality display differences between layouts

### Decision

Complete layout uses `LoraSignalIndicator` / `NodeSignalQuality` composables (quality icon + SNR/RSSI text). Compact layout uses a single colored icon via the `Quality` enum from `determineSignalQuality(snr, rssi)`.

### Rationale

- The `NodeSignalQuality` FlowRow provides detailed visual feedback (SNR value, RSSI value, quality label) but requires horizontal space that the compact layout cannot afford.
- A single colored icon conveys the essential information (Good/Fair/Bad/None) at compact density.
- The same `determineSignalQuality(snr, rssi)` function drives both representations, ensuring consistent thresholds.

### Alternatives considered

- **Same composable in both layouts**: Rejected — the `NodeSignalQuality` FlowRow is too wide for the compact combined-icons row.
- **No signal indicator in compact**: Rejected — signal quality is one of the most useful at-a-glance metrics.
- **Numeric SNR value in compact**: Rejected — requires domain knowledge to interpret; color-coding is more accessible.

### Consequences

- The help sheet must document both representations so users understand the relationship between the compact icon colors and the Complete layout's `LoraSignalIndicator` display.

---

## R-005: Settings section integration

### Decision

Add the Node Layout section as part of the existing App Settings screen in `feature/settings`, not as a separate navigation destination.

### Rationale

- Node layout preferences are app-level display settings, not device configuration. They belong alongside other app preferences.
- The existing settings screen already hosts similar UI preference sections.
- No new navigation route is required, keeping the route table compact.

### Alternatives considered

- **Dedicated settings sub-screen**: Rejected — the content (1 picker + 9 toggles + 1 preview) fits within a single scrollable section without needing its own route.
- **Inline controls on the node list itself**: Rejected — pollutes the node list UI and creates discoverability issues.

### Consequences

- The live preview must be lightweight enough to render inline within the settings scroll without janking.
- If the settings screen grows too long in the future, the Node Layout section can be extracted to a sub-screen without changing the preference model.
